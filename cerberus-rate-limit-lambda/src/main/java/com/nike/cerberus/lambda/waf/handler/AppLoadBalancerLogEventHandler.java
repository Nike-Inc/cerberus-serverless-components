package com.nike.cerberus.lambda.waf.handler;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.waf.AWSWAFRegional;
import com.amazonaws.services.waf.AWSWAFRegionalClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fieldju.slackclient.Message;
import com.fieldju.slackclient.SlackClient;
import com.google.common.annotations.VisibleForTesting;
import com.nike.cerberus.lambda.waf.AppLoadBalancerLogEvent;
import com.nike.cerberus.lambda.waf.RateLimitConfig;
import com.nike.cerberus.lambda.waf.processor.GoogleAnalyticsKPIProcessor;
import com.nike.cerberus.lambda.waf.processor.Processor;
import com.nike.cerberus.lambda.waf.processor.RateLimitingProcessor;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * This is the Main Class for our WAF Lambda that will process logs and deal with ip addresses that are seen as abusive.
 * It ingests logs from CloudFront http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html
 */
public class AppLoadBalancerLogEventHandler {

    private final Logger log = Logger.getLogger(getClass());

    private final AmazonS3 amazonS3Client;

    private final ObjectMapper objectMapper;

    private final RateLimitConfig rateLimitConfig;

    private List<Processor> logEventProcessors = new LinkedList<>();

    public AppLoadBalancerLogEventHandler() {
        this(new AmazonS3Client(),
                AWSWAFRegionalClientBuilder.standard()
                        .withRegion(Regions.US_WEST_2)
                        .build(),
                new RateLimitConfig());
    }

    public AppLoadBalancerLogEventHandler(AmazonS3 amazonS3Client,
                                          AWSWAFRegional awsWaf,
                                          RateLimitConfig rateLimitConfig) {
        this.amazonS3Client = amazonS3Client;
        this.rateLimitConfig = rateLimitConfig;
        objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        // New processors would need there own ip sets as there is a hard limit of 1000 ips and the
        // RateLimitingProcessor truncates the set and removes any ips from the set that it doesn't know about
        // see CloudFormationDefinedParams
        logEventProcessors.add(new RateLimitingProcessor(objectMapper, awsWaf, amazonS3Client));
        logEventProcessors.add(new GoogleAnalyticsKPIProcessor());
    }

    @VisibleForTesting
    protected void overrideProcessors(List<Processor> logEventProcessors) {
        this.logEventProcessors = logEventProcessors;
    }

    /**
     * The handler that will get triggered by the CloudFront adding a new log chunk into the CloudFront Log S3 Bucket.
     * Streams the log from S3 and processes each line, which represents a request to Cerberus.
     * http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html#LogFileFormat
     */
    public void handleNewS3Event(S3Event event) throws IOException {
        for (S3EventNotification.S3EventNotificationRecord s3EventNotificationRecord : event.getRecords()){
            String bucketName = s3EventNotificationRecord.getS3().getBucket().getName();
            String key = s3EventNotificationRecord.getS3().getObject().getKey();

            // Only process the ALB access log files (they end in .log.gz)
            if (! key.endsWith(".log.gz")) {
                return;
            }

            log.info(String.format("Triggered from %s/%s", bucketName, key));
            S3Object logObject  = amazonS3Client.getObject(new GetObjectRequest(bucketName, key));
            List<AppLoadBalancerLogEvent> logEvents = ingestLogStream(logObject.getObjectContent());

            logEventProcessors.forEach(processor -> {
                try {
                    processor.processLogEvents(logEvents, rateLimitConfig, bucketName);
                } catch (Throwable t) {
                    log.error(String.format("Failed to run log processor %s", processor.getClass()), t);

                    // Send a message to slack if its configured to do so
                    if (StringUtils.isNotBlank(rateLimitConfig.getSlackWebHookUrl())) {
                        String text = String.format("Failed to run log processor %s, env: %s reason: %s",
                                processor.getClass(), rateLimitConfig.getEnv(), t.getMessage());
                        Message.Builder builder = new Message.Builder(text).userName("Cloud-Front-Event-Handler");

                        if (StringUtils.startsWith(rateLimitConfig.getSlackIcon(), "http")) {
                            builder.iconUrl(rateLimitConfig.getSlackIcon());
                        } else {
                            builder.iconEmoji(rateLimitConfig.getSlackIcon());
                        }

                        new SlackClient(rateLimitConfig.getSlackWebHookUrl()).sendMessage(builder.build());
                    }
                }
            });
        }
    }

    /**
     * Process the log from s3 and breaks it down to a list of events
     * @param stream The stream from s3
     * @return a list of AppLoadBalancerLogEvents to be processed by the processors
     * @throws IOException
     */
    protected List<AppLoadBalancerLogEvent> ingestLogStream(S3ObjectInputStream stream) throws IOException {
        List<AppLoadBalancerLogEvent> logEvents = new LinkedList<>();

        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(stream);
            Reader decoder = new InputStreamReader(gzipInputStream);
            BufferedReader bufferedReader = new BufferedReader(decoder);

            String request;
            while ((request = bufferedReader.readLine()) != null) {
                logEvents.add(new AppLoadBalancerLogEvent(request));
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        return logEvents;
    }
}