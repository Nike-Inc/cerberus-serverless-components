package com.nike.cerberus.lambda.waf.handler;

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
import com.google.common.collect.Lists;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;
import com.nike.cerberus.lambda.waf.processor.Processor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CloudFrontLogEventHandlerTest {

    CloudFrontLogEventHandler handler;

    @Mock
    AmazonCloudFormationClient cloudFormationClient;

    @Mock
    AmazonS3Client amazonS3Client;

    @Mock
    AWSWAF awsWaf;

    @Before
    public void before() {
        initMocks(this);
        handler = spy(new CloudFrontLogEventHandler(cloudFormationClient, amazonS3Client, awsWaf));
    }

    @Test
    public void testThatIngestLogStreamReturnsAValidListOfEvents() throws IOException {
        InputStream logStream = getClass().getClassLoader().getResourceAsStream("access.log.gz");

        S3ObjectInputStream s3ObjectInputStream = new S3ObjectInputStream(logStream, null);

        List<CloudFrontLogEvent> events = handler.ingestLogStream(s3ObjectInputStream);

        assertEquals(3, events.size());
    }

    @Test
    public void testThatHandleEventCallsProcessEventsOnTheProcessors() throws IOException {
        String bucketName = "bucketname";
        String arn = "foo";

        Processor processor = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);

        handler.overrideProcessors(processors);
        CloudFrontLogHandlerConfig params = new CloudFrontLogHandlerConfig();
        doReturn(params).when(handler).getConfiguration(arn);

        Context context = mock(Context.class);
        when(context.getInvokedFunctionArn()).thenReturn(arn);

        S3Event event = mock(S3Event.class);
        List<S3EventNotification.S3EventNotificationRecord> records = Lists.newArrayList();
        S3EventNotification.S3EventNotificationRecord record = mock(S3EventNotification.S3EventNotificationRecord.class);
        records.add(record);
        when(event.getRecords()).thenReturn(records);
        S3EventNotification.S3Entity s3Entity = mock(S3EventNotification.S3Entity.class);
        S3EventNotification.S3BucketEntity bucketEntity = mock(S3EventNotification.S3BucketEntity.class);
        S3EventNotification.S3ObjectEntity objectEntity = mock(S3EventNotification.S3ObjectEntity.class);
        when(s3Entity.getBucket()).thenReturn(bucketEntity);
        when(s3Entity.getObject()).thenReturn(objectEntity);
        when(record.getS3()).thenReturn(s3Entity);
        when(bucketEntity.getName()).thenReturn(bucketName);
        when(objectEntity.getKey()).thenReturn("access.log.gz");
        when(amazonS3Client.getObject(isA(GetObjectRequest.class))).thenReturn(mock(S3Object.class));
        doReturn(null).when(handler).ingestLogStream(null);

        handler.handleNewS3Event(event, context);

        verify(processor, times(1)).processLogEvents(null, params, bucketName);
    }

    @Test
    public void testThatHandleEventCallsDoesNotProcessEventsOnTheProcessorsWhenNotALogFile() throws IOException {
        String bucketName = "bucketname";
        String arn = "foo";

        Processor processor = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);

        handler.overrideProcessors(processors);
        CloudFrontLogHandlerConfig params = new CloudFrontLogHandlerConfig();
        doReturn(params).when(handler).getConfiguration(arn);

        Context context = mock(Context.class);
        when(context.getInvokedFunctionArn()).thenReturn(arn);

        S3Event event = mock(S3Event.class);
        List<S3EventNotification.S3EventNotificationRecord> records = Lists.newArrayList();
        S3EventNotification.S3EventNotificationRecord record = mock(S3EventNotification.S3EventNotificationRecord.class);
        records.add(record);
        when(event.getRecords()).thenReturn(records);
        S3EventNotification.S3Entity s3Entity = mock(S3EventNotification.S3Entity.class);
        S3EventNotification.S3BucketEntity bucketEntity = mock(S3EventNotification.S3BucketEntity.class);
        S3EventNotification.S3ObjectEntity objectEntity = mock(S3EventNotification.S3ObjectEntity.class);
        when(s3Entity.getBucket()).thenReturn(bucketEntity);
        when(s3Entity.getObject()).thenReturn(objectEntity);
        when(record.getS3()).thenReturn(s3Entity);
        when(bucketEntity.getName()).thenReturn(bucketName);
        when(objectEntity.getKey()).thenReturn("data.json");
        when(amazonS3Client.getObject(isA(GetObjectRequest.class))).thenReturn(mock(S3Object.class));
        doReturn(null).when(handler).ingestLogStream(null);

        handler.handleNewS3Event(event, context);

        verify(processor, times(0)).processLogEvents(null, params, bucketName);
    }

    @Test
    public void testThatHandleEventDoesNotExplodeWhenTheFirstProcessorErrorsOut() throws IOException {
        String bucketName = "bucketname";
        String arn = "foo";

        Processor processor = mock(Processor.class);
        Processor processor2 = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);
        doThrow(new RuntimeException("foo")).when(processor).processLogEvents(any(), any(), any());
        processors.add(processor2);

        handler.overrideProcessors(processors);
        CloudFrontLogHandlerConfig params = new CloudFrontLogHandlerConfig();
        doReturn(params).when(handler).getConfiguration(arn);

        Context context = mock(Context.class);
        when(context.getInvokedFunctionArn()).thenReturn(arn);

        S3Event event = mock(S3Event.class);
        List<S3EventNotification.S3EventNotificationRecord> records = Lists.newArrayList();
        S3EventNotification.S3EventNotificationRecord record = mock(S3EventNotification.S3EventNotificationRecord.class);
        records.add(record);
        when(event.getRecords()).thenReturn(records);
        S3EventNotification.S3Entity s3Entity = mock(S3EventNotification.S3Entity.class);
        S3EventNotification.S3BucketEntity bucketEntity = mock(S3EventNotification.S3BucketEntity.class);
        S3EventNotification.S3ObjectEntity objectEntity = mock(S3EventNotification.S3ObjectEntity.class);
        when(s3Entity.getBucket()).thenReturn(bucketEntity);
        when(s3Entity.getObject()).thenReturn(objectEntity);
        when(record.getS3()).thenReturn(s3Entity);
        when(bucketEntity.getName()).thenReturn(bucketName);
        when(objectEntity.getKey()).thenReturn("access.log.gz");
        when(amazonS3Client.getObject(isA(GetObjectRequest.class))).thenReturn(mock(S3Object.class));
        doReturn(null).when(handler).ingestLogStream(null);

        handler.handleNewS3Event(event, context);

        verify(processor, times(1)).processLogEvents(null, params, bucketName);
        verify(processor2, times(1)).processLogEvents(null, params, bucketName);
    }

    @Test
    public void testThatHandlerCanDeriveS3BucketAndGetConfig() {
        String arn = "arn:aws:lambda:us-west-2:1111111:function:dev-gateway-fas342452-6d86-LambdaWAFBlacklistingFun-1LSORI5GUP95H";
        String bucketName = "dev-cerberusconfigbucket";
        String confJson = "{\n" +
                "  \"manual_white_list_ip_set_id\" : \"11111-a3be-41ee-2222-33f708dd939e\",\n" +
                "  \"manual_black_list_ip_set_id\" : \"11111-569d-4924-22222-33333\",\n" +
                "  \"rate_limit_auto_black_list_ip_set_id\" : \"33333-5195-4b32-44444-71d8fbb9ff4d\",\n" +
                "  \"rate_limit_violation_blacklist_period_in_minutes\" : 10,\n" +
                "  \"request_per_minute_limit\" : 10\n" +
                "}";

        List<Bucket> bucketList = Lists.newLinkedList();
        bucketList.add(new Bucket(bucketName));

        when(amazonS3Client.listBuckets()).thenReturn(bucketList);

        S3Object object = new S3Object();
        object.setObjectContent(new ByteArrayInputStream(confJson.getBytes()));

        when(amazonS3Client.getObject(any())).thenReturn(object);

        CloudFrontLogHandlerConfig config = handler.getConfiguration(arn);

        assertTrue(config.getRequestPerMinuteLimit() == 10);
    }

    @Test(expected = RuntimeException.class)
    public void testThatHandlerErrorsWhenWeCantFindBucket() {
        String arn = "arn:aws:lambda:us-west-2:1111111:function:dev-gateway-fas342452-6d86-LambdaWAFBlacklistingFun-1LSORI5GUP95H";
        String bucketName = "prod-cerberusconfigbucket";
        String bucketName2 = "test-foo";

        List<Bucket> bucketList = Lists.newLinkedList();
        bucketList.add(new Bucket(bucketName));
        bucketList.add(new Bucket(bucketName2));

        when(amazonS3Client.listBuckets()).thenReturn(bucketList);

        handler.getConfiguration(arn);
    }

    @Test(expected = RuntimeException.class)
    public void testThatHandlerErrorsWhenWeCantFindTheConfigFile() {
        String arn = "arn:aws:lambda:us-west-2:1111111:function:dev-gateway-fas342452-6d86-LambdaWAFBlacklistingFun-1LSORI5GUP95H";
        String bucketName = "dev-cerberusconfigbucket";

        List<Bucket> bucketList = Lists.newLinkedList();
        bucketList.add(new Bucket(bucketName));

        AmazonS3Exception e = new AmazonS3Exception("foo");
        e.setErrorCode("NoSuchKey");

        when(amazonS3Client.getObject(any())).thenThrow(e);

        when(amazonS3Client.listBuckets()).thenReturn(bucketList);

        handler.getConfiguration(arn);
    }

    @Test(expected = RuntimeException.class)
    public void testThatHandlerErrorsWhenWeCantFindTheConfigFile2() {
        String arn = "arn:aws:lambda:us-west-2:1111111:function:dev-gateway-fas342452-6d86-LambdaWAFBlacklistingFun-1LSORI5GUP95H";
        String bucketName = "dev-cerberusconfigbucket";

        List<Bucket> bucketList = Lists.newLinkedList();
        bucketList.add(new Bucket(bucketName));

        when(amazonS3Client.getObject(any())).thenReturn(null);

        when(amazonS3Client.listBuckets()).thenReturn(bucketList);

        handler.getConfiguration(arn);
    }
}
