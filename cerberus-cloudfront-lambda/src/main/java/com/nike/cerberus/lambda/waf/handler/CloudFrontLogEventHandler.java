package com.nike.cerberus.lambda.waf.handler;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.waf.AWSWAF;
import com.amazonaws.services.waf.AWSWAFClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fieldju.slackclient.Message;
import com.fieldju.slackclient.SlackClient;
import com.google.common.annotations.VisibleForTesting;
import com.nike.cerberus.lambda.waf.processor.GoogleAnalyticsKPIProcessor;
import com.nike.cerberus.lambda.waf.processor.Processor;
import com.nike.cerberus.lambda.waf.processor.RateLimitingProcessor;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;
import com.nike.cerberus.lambda.waf.processor.TlsVerificationProcessor;
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
public class CloudFrontLogEventHandler {

    public static final String CERBERUS_CONFIG_BUCKET = "cerberusconfigbucket";
    private static final String CONFIG_FILE_NAME = "data/cloud-front-log-processor/lambda-config.json";

    private final Logger log = Logger.getLogger(getClass());
    private final AmazonS3Client amazonS3Client;
    private final ObjectMapper objectMapper;

    private List<Processor> logEventProcessors = new LinkedList<>();

    private String configBucket;

    public CloudFrontLogEventHandler() {
        this(new AmazonCloudFormationClient(), new AmazonS3Client(), new AWSWAFClient());
    }

    public CloudFrontLogEventHandler(AmazonCloudFormationClient cloudFormationClient,
                                     AmazonS3Client amazonS3Client,
                                     AWSWAF awsWaf) {

        cloudFormationClient.setRegion(Region.getRegion(Regions.US_WEST_2));
        this.amazonS3Client = amazonS3Client;
        objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        // New processors would need there own ip sets as there is a hard limit of 1000 ips and the
        // RateLimitingProcessor truncates the set and removes any ips from the set that it doesn't know about
        // see CloudFormationDefinedParams
        logEventProcessors.add(new RateLimitingProcessor(objectMapper, awsWaf, amazonS3Client));
        logEventProcessors.add(new GoogleAnalyticsKPIProcessor());
        logEventProcessors.add(new TlsVerificationProcessor());
    }

    @VisibleForTesting
    protected void overrideProcessors(List<Processor> logEventProcessors) {
        this.logEventProcessors = logEventProcessors;
    }

    /**
     * The handler that will get triggered by the CloudFront adding a new log chunk into the CloudFront Log S3 Bucket.
     * Streams the log from S3 and processes each line, which represents a request to Cerberus.
     * http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html#LogFileFormat
     *
     * @param context, the context of the lambda fn
     */
    public void handleNewS3Event(S3Event event, Context context) throws IOException {
        CloudFrontLogHandlerConfig config =
                getConfiguration(context.getInvokedFunctionArn());

        log.info(String.format("Found CloudFormation stack and derived params: %s",
                objectMapper.writeValueAsString(config)));

        for (S3EventNotification.S3EventNotificationRecord s3EventNotificationRecord : event.getRecords()){
            String bucketName = s3EventNotificationRecord.getS3().getBucket().getName();
            String key = s3EventNotificationRecord.getS3().getObject().getKey();

            // Only process the log files from CF they end in .gz
            if (! key.endsWith(".gz")) {
                return;
            }

            log.info(String.format("Triggered from %s/%s", bucketName, key));
            S3Object logObject  = amazonS3Client.getObject(new GetObjectRequest(bucketName, key));
            List<CloudFrontLogEvent> logEvents = ingestLogStream(logObject.getObjectContent());

            logEventProcessors.forEach(processor -> {
                try {
                    processor.processLogEvents(logEvents, config, bucketName);
                } catch (Throwable t) {
                    log.error(String.format("Failed to run log processor %s", processor.getClass()), t);

                    // Send a message to slack if its configured to do so
                    if (StringUtils.isNotBlank(config.getSlackWebHookUrl())) {
                        String text = String.format("Failed to run log processor %s, env: %s reason: %s",
                                processor.getClass(), config.getEnv(), t.getMessage());
                        Message.Builder builder = new Message.Builder(text).userName("Cloud-Front-Event-Handler");

                        if (StringUtils.startsWith(config.getSlackIcon(), "http")) {
                            builder.iconUrl(config.getSlackIcon());
                        } else {
                            builder.iconEmoji(config.getSlackIcon());
                        }

                        new SlackClient(config.getSlackWebHookUrl()).sendMessage(builder.build());
                    }
                }
            });
        }
    }

    /**
     * Proccess the log from s3 and breaks it down to a list of events
     * @param stream The stream from s3
     * @return a list of CloudFrontLogEvent to be processed by the processors
     * @throws IOException
     */
    protected List<CloudFrontLogEvent> ingestLogStream(S3ObjectInputStream stream) throws IOException {
        List<CloudFrontLogEvent> logEvents = new LinkedList<>();

        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(stream);
            Reader decoder = new InputStreamReader(gzipInputStream);
            BufferedReader bufferedReader = new BufferedReader(decoder);

            String request;
            while ((request = bufferedReader.readLine()) != null) {
                // ignore comment lines
                if (request.startsWith("#")) {
                    if (request.contains("Version")) {
                        // This lambda was written for V1 log format lets explode if the version ever gets bumped
                        assert request.contains("Version: 1.0");
                    }
                    continue;
                }
                logEvents.add(new CloudFrontLogEvent(request));
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        return logEvents;
    }

    /**
     * Retrieves the CloudFormation stack outputs that define needed params for this Lambda to work
     *
     * @param arn The arn of the current lambda function
     * @return CloudFormationDefinedParams bean with the params we need for this function.
     */
    protected CloudFrontLogHandlerConfig getConfiguration(String arn) {
        String environmentName = arn.split(":")[6].split("-")[0];

        if (configBucket == null) {
            configBucket = getConfigBucketName(environmentName);
        }

        S3Object s3Object;
        try {
            s3Object = amazonS3Client.getObject(new GetObjectRequest(configBucket, CONFIG_FILE_NAME));
        } catch (AmazonS3Exception e) {
            throw new RuntimeException("Failed to find config file for this lambda", e);
        }

        try {
            CloudFrontLogHandlerConfig config = objectMapper.readValue(s3Object.getObjectContent(), CloudFrontLogHandlerConfig.class);
            config.setEnv(environmentName);
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize json data from previous runs", e);
        }
    }

    protected String getConfigBucketName(String environmentName) {
        List<Bucket> buckets = amazonS3Client.listBuckets();

        for (final Bucket bucket : buckets) {
            if (StringUtils.contains(bucket.getName(), CERBERUS_CONFIG_BUCKET)) {
                String[] parts = bucket.getName().split("-");
                if (StringUtils.equalsIgnoreCase(environmentName, parts[0])) {
                    return bucket.getName();
                }
            }
        }
        throw new RuntimeException("Failed to determine the config s3 bucket");
    }
}
