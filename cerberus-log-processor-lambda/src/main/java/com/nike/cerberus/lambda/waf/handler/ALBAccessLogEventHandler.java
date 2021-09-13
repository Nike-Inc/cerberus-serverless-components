package com.nike.cerberus.lambda.waf.handler;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.waf.AWSWAFRegional;
import com.amazonaws.services.waf.AWSWAFRegionalClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fieldju.slackclient.Message;
import com.fieldju.slackclient.SlackClient;
import com.google.common.annotations.VisibleForTesting;
import com.nike.cerberus.lambda.waf.ALBAccessLogEvent;
import com.nike.cerberus.lambda.waf.AthenaService;
import com.nike.cerberus.lambda.waf.LogProcessorLambdaConfig;
import com.nike.cerberus.lambda.waf.processor.Processor;
import com.nike.cerberus.lambda.waf.processor.RateLimitingProcessor;
import com.nike.cerberus.lambda.waf.processor.TlsVerificationProcessor;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is the Main Class for our WAF Lambda that will process logs and deal with ip addresses that are seen as abusive.
 * It ingests logs from the ALB http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
 */
public class ALBAccessLogEventHandler {

    private final Logger log = Logger.getLogger(getClass());

    private final ObjectMapper objectMapper;

    private final LogProcessorLambdaConfig logProcessorLambdaConfig;

    private List<Processor> logEventProcessors = new LinkedList<>();

    private AthenaService athenaService;

    public ALBAccessLogEventHandler() {
        this(AmazonS3ClientBuilder.standard()
              .withRegion(System.getenv("AWS_REGION")).build(),
        AWSWAFRegionalClientBuilder.standard()
              .withRegion(System.getenv("AWS_REGION")).build(),
        new LogProcessorLambdaConfig());
    }

    public ALBAccessLogEventHandler(AmazonS3 amazonS3Client,
                                    AWSWAFRegional awsWaf,
                                    LogProcessorLambdaConfig logProcessorLambdaConfig) {

        this.logProcessorLambdaConfig = logProcessorLambdaConfig;
        objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // New processors would need there own ip sets as there is a hard limit of 1000 ips and the
        // RateLimitingProcessor truncates the set and removes any ips from the set that it doesn't know about
        // see CloudFormationDefinedParams
        logEventProcessors.add(new RateLimitingProcessor(objectMapper, awsWaf, amazonS3Client));
        logEventProcessors.add(new TlsVerificationProcessor());

        athenaService = new AthenaService(logProcessorLambdaConfig);
    }

    @VisibleForTesting
    protected void overrideProcessors(List<Processor> logEventProcessors) {
        this.logEventProcessors = logEventProcessors;
    }

    /**
     * The handler that's triggered by a scheduled event.
     * Query Athena for requests made to Cerberus within the last interval.
     * http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
     */
    public void handleScheduledEvent() {
        List<ALBAccessLogEvent> logEvents = getLogEvents();
        String bucketName = logProcessorLambdaConfig.getAlbLogBucketName();

        logEventProcessors.forEach(processor -> {
            try {
                processor.processLogEvents(logEvents, logProcessorLambdaConfig, bucketName);
            } catch (Throwable t) {
                log.error(String.format("Failed to run log processor %s", processor.getClass()), t);

                // Send a message to slack if its configured to do so
                if (StringUtils.isNotBlank(logProcessorLambdaConfig.getSlackWebHookUrl())) {
                    String text = String.format("Failed to run log processor %s, env: %s reason: %s",
                            processor.getClass(), logProcessorLambdaConfig.getEnv(), t.getMessage());
                    Message.Builder builder = new Message.Builder(text).userName("ALB-Access-Log-Event-Handler");

                    if (StringUtils.startsWith(logProcessorLambdaConfig.getSlackIcon(), "http")) {
                        builder.iconUrl(logProcessorLambdaConfig.getSlackIcon());
                    } else {
                        builder.iconEmoji(logProcessorLambdaConfig.getSlackIcon());
                    }

                    new SlackClient(logProcessorLambdaConfig.getSlackWebHookUrl()).sendMessage(builder.build());
                }
            }
        });
    }

    /**
     * Query Athena and convert the result to a list of events
     * @return a list of AppLoadBalancerLogEvents to be processed by the processors
     */
    protected List<ALBAccessLogEvent> getLogEvents() {
        Integer intervalInMins = logProcessorLambdaConfig.getIntervalInMins();
        DateTime intervalBeforeNow = DateTime.now().minusMinutes(intervalInMins);
        return athenaService.getLogEntrysAfter(intervalBeforeNow).stream()
                .map(ALBAccessLogEvent::new).collect(Collectors.toList());
    }

    public void setAthenaService(AthenaService athenaService) {
        this.athenaService = athenaService;
    }
}
