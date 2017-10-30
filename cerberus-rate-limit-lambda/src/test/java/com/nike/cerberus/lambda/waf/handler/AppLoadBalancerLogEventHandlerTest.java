package com.nike.cerberus.lambda.waf.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.waf.AWSWAFRegional;
import com.google.common.collect.Lists;
import com.nike.cerberus.lambda.waf.AppLoadBalancerLogEvent;
import com.nike.cerberus.lambda.waf.RateLimitConfig;
import com.nike.cerberus.lambda.waf.processor.Processor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class AppLoadBalancerLogEventHandlerTest {

    AppLoadBalancerLogEventHandler handler;

    @Mock
    AmazonS3Client amazonS3Client;

    @Mock
    AWSWAFRegional awsWaf;

    @Mock
    RateLimitConfig rateLimitConfig;

    @Before
    public void before() throws IOException {
        initMocks(this);
        handler = spy(new AppLoadBalancerLogEventHandler(amazonS3Client, awsWaf, rateLimitConfig));
    }

    @Test
    public void test_that_quotations_in_log_are_not_separated() {
        String request = "\"GET https://cerberus.oss.nike.com:443/dashboard?x=y HTTP/2.0\"";
        String userAgent = "\"User Agent stuff\"";
        String str = "h2 2017-10-02T17:48:24.305799Z app/name-balancer-aaa/bbb 1.1.1.1:17454 1.2.0.6:8443 0.015 0.002 0.000 301 301 242 116 " + request + " " + userAgent + " ECDHE-RSA-AES128-GCM-SHA256 TLSv1.2 arn:aws:elasticloadbalancing:us-west-2:111111:targetgroup/env-https-target-name/00000 \"Root=1-59d27be8-3ef5870d62321261398f1a8c\" \"cerberus.oss.nike.com\" \"arn:aws:iam::0000:server-certificate/group/cerb/env/cms_1111\"";

        AppLoadBalancerLogEvent event = new AppLoadBalancerLogEvent(str);
        assertEquals(userAgent, event.getUserAgent());
    }

    @Test
    public void test_that_request_url_is_parse_correctly() {
        String request = "\"GET https://cerberus.oss.nike.com:443/dashboard?x=y HTTP/2.0\"";
        String userAgent = "\"User Agent stuff\"";
        String str = "h2 2017-10-02T17:48:24.305799Z app/name-balancer-aaa/bbb 1.1.1.1:17454 1.2.0.6:8443 0.015 0.002 0.000 301 301 242 116 " + request + " " + userAgent + " ECDHE-RSA-AES128-GCM-SHA256 TLSv1.2 arn:aws:elasticloadbalancing:us-west-2:111111:targetgroup/env-https-target-name/00000 \"Root=1-59d27be8-3ef5870d62321261398f1a8c\" \"cerberus.oss.nike.com\" \"arn:aws:iam::0000:server-certificate/group/cerb/env/cms_1111\"";

        AppLoadBalancerLogEvent event = new AppLoadBalancerLogEvent(str);
        assertEquals("443", event.getRequestPort());
        assertEquals("cerberus.oss.nike.com", event.getHostname());
        assertEquals("/dashboard?x=y", event.getRequestUri());
    }


    @Test
    public void testThatIngestLogStreamReturnsAValidListOfEvents() throws IOException {
        InputStream logStream = getClass().getClassLoader().getResourceAsStream("access.log.gz");

        S3ObjectInputStream s3ObjectInputStream = new S3ObjectInputStream(logStream, null);

        List<AppLoadBalancerLogEvent> events = handler.ingestLogStream(s3ObjectInputStream);

        assertEquals(4, events.size());
    }

    @Test
    public void testThatHandleEventCallsProcessEventsOnTheProcessors() throws IOException {
        String bucketName = "bucketname";
        String arn = "foo";

        Processor processor = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);

        handler.overrideProcessors(processors);

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

        handler.handleNewS3Event(event);

        verify(processor, times(1)).processLogEvents(null, rateLimitConfig, bucketName);
    }

    @Test
    public void testThatHandleEventCallsDoesNotProcessEventsOnTheProcessorsWhenNotALogFile() throws IOException {
        String bucketName = "bucketname";
        String arn = "foo";

        Processor processor = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);

        handler.overrideProcessors(processors);
        RateLimitConfig params = mock(RateLimitConfig.class);

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

        handler.handleNewS3Event(event);

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
        RateLimitConfig params = mock(RateLimitConfig.class);

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

        handler.handleNewS3Event(event);

        verify(processor, times(1)).processLogEvents(null, rateLimitConfig, bucketName);
        verify(processor2, times(1)).processLogEvents(null, rateLimitConfig, bucketName);
    }
}
