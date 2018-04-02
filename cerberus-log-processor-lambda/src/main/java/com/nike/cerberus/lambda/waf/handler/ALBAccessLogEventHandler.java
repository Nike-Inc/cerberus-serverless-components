package com.nike.cerberus.lambda.waf.handler;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.waf.AWSWAFRegional;
import com.amazonaws.services.waf.AWSWAFRegionalClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
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

    private final AmazonS3 amazonS3Client;

    private final ObjectMapper objectMapper;

    private final LogProcessorLambdaConfig logProcessorLambdaConfig;

    private List<Processor> logEventProcessors = new LinkedList<>();

    private AthenaService athenaService;

    public ALBAccessLogEventHandler() {
        this(new AmazonS3Client(),
                AWSWAFRegionalClientBuilder.standard()
                        .withRegion(Regions.US_WEST_2)
                        .build(),
                new LogProcessorLambdaConfig());
    }

    public ALBAccessLogEventHandler(AmazonS3 amazonS3Client,
                                    AWSWAFRegional awsWaf,
                                    LogProcessorLambdaConfig logProcessorLambdaConfig) {

        this.amazonS3Client = amazonS3Client;
        this.logProcessorLambdaConfig = logProcessorLambdaConfig;
        objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

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
     * Query Athena for requests made to Cerberus within the last hour.
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
        DateTime oneHourBeforeNow = DateTime.now().minus(3600 *1000);
        return athenaService.getLogEntrysAfter(oneHourBeforeNow).stream()
                .map(ALBAccessLogEvent::new).collect(Collectors.toList());
    }

    public void setAthenaService(AthenaService athenaService) {
        this.athenaService = athenaService;
    }
}
