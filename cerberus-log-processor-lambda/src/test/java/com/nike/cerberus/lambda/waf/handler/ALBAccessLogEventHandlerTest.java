package com.nike.cerberus.lambda.waf.handler;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.waf.AWSWAFRegional;
import com.google.common.collect.Lists;
import com.nike.cerberus.lambda.waf.ALBAccessLogEvent;
import com.nike.cerberus.lambda.waf.AthenaService;
import com.nike.cerberus.lambda.waf.LogProcessorLambdaConfig;
import com.nike.cerberus.lambda.waf.processor.Processor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class ALBAccessLogEventHandlerTest {

    ALBAccessLogEventHandler handler;

    @Mock
    AmazonS3Client amazonS3Client;

    @Mock
    AWSWAFRegional awsWaf;

    @Mock
    LogProcessorLambdaConfig logProcessorLambdaConfig;

    @Mock
    AthenaService athenaService;

    List<List<String>> events = Arrays.asList(
            Arrays.asList("h2", "2017-10-02T17:48:55.882507Z", "app/foo/balancer", "1.1.1.1", "45745", "2.2.0.8", "8443", "-1", "-1", "-1", "504", "-", "265", "620", "GET", "https://cerberus.oss.nike.com:443/dashboard/", "HTTP/2.0", "\"User Agent stuff\"", "ECDHE-RSA-AES128-GCM-SHA256", "TLSv1.2", "arn:aws:elasticloadbalancing:us-west-2:00000:targetgroup/target-group-name/99cbf5b80ac385c5", "\"Root=1-59d27be8-3ef5870d62321261398f1a8c\" \"perf1.cerberus.nikecloud.com\"", "arn:aws:iam::933764306573:server-certificate/cloudfront/cerberus/perf1/cms_1ffde2bc-e8c9-4521-b2b0-4d4e5fd00fc6", "0", "2017-10-02"),
            Arrays.asList("h2", "2017-10-02T17:48:55.882507Z", "app/foo/balancer", "1.1.1.1", "45745", "2.2.0.8", "8443", "-1", "-1", "-1", "504", "-", "265", "620", "GET", "https://cerberus.oss.nike.com:443/dashboard/", "HTTP/2.0", "\"User Agent stuff\"", "ECDHE-RSA-AES128-GCM-SHA256", "TLSv1.2", "arn:aws:elasticloadbalancing:us-west-2:00000:targetgroup/target-group-name/99cbf5b80ac385c5", "\"Root=1-59d27be8-20bb91ec34929945336cb601\" \"perf1.cerberus.nikecloud.com\"", "arn:aws:iam::933764306573:server-certificate/cloudfront/cerberus/perf1/cms_1ffde2bc-e8c9-4521-b2b0-4d4e5fd00fc6", "0", "2017-10-02"),
            Arrays.asList("h2", "2017-10-02T17:48:55.882507Z", "app/foo/balancer", "1.1.1.1", "45745", "2.2.0.8", "8443", "-1", "-1", "-1", "504", "-", "265", "620", "GET", "https://cerberus.oss.nike.com:443/dashboard/", "HTTP/2.0", "\"User Agent stuff\"", "ECDHE-RSA-AES128-GCM-SHA256", "TLSv1.2", "arn:aws:elasticloadbalancing:us-west-2:00000:targetgroup/target-group-name/99cbf5b80ac385c5", "\"Root=1-59d27be8-5f4ea6646ffc7f454c328fe2\" \"perf1.cerberus.nikecloud.com\"", "arn:aws:iam::933764306573:server-certificate/cloudfront/cerberus/perf1/cms_1ffde2bc-e8c9-4521-b2b0-4d4e5fd00fc6", "0", "2017-10-02"),
            Arrays.asList("h2", "2017-10-02T17:48:55.882507Z", "app/foo/balancer", "1.1.1.1", "45745", "2.2.0.8", "8443", "-1", "-1", "-1", "504", "-", "265", "620", "GET", "https://cerberus.oss.nike.com:443/dashboard/", "HTTP/2.0", "\"User Agent stuff\"", "ECDHE-RSA-AES128-GCM-SHA256", "TLSv1.2", "arn:aws:elasticloadbalancing:us-west-2:00000:targetgroup/target-group-name/99cbf5b80ac385c5", "\"Root=1-59d27bfd-54d147ba357f3bd267d9d6e5\" \"perf1.cerberus.nikecloud.com\"", "session-renegotiated-or-reused\"", "0", "2017-10-02"));

    @Before
    public void before() throws IOException {
        initMocks(this);
        doReturn("arn:aws:iam::123123123:role/foo").when(logProcessorLambdaConfig).getIamPrincipalArn();
        doReturn(Regions.US_WEST_2).when(logProcessorLambdaConfig).getRegion();
        doReturn("bucketname").when(logProcessorLambdaConfig).getAlbLogBucketName();
        handler = spy(new ALBAccessLogEventHandler(amazonS3Client, awsWaf, logProcessorLambdaConfig));
    }

    @Test
    public void test_that_quotations_in_log_are_not_separated() {
        String request = "\"GET https://cerberus.oss.nike.com:443/dashboard?x=y HTTP/2.0\"";
        String userAgent = "\"User Agent stuff\"";
        String str = "h2 2017-10-02T17:48:24.305799Z app/name-balancer-aaa/bbb 1.1.1.1:17454 1.2.0.6:8443 0.015 0.002 0.000 301 301 242 116 " + request + " " + userAgent + " ECDHE-RSA-AES128-GCM-SHA256 TLSv1.2 arn:aws:elasticloadbalancing:us-west-2:111111:targetgroup/env-https-target-name/00000 \"Root=1-59d27be8-3ef5870d62321261398f1a8c\" \"cerberus.oss.nike.com\" \"arn:aws:iam::0000:server-certificate/group/cerb/env/cms_1111\"";

        ALBAccessLogEvent event = new ALBAccessLogEvent(str);
        assertEquals(userAgent, event.getUserAgent());
    }

    @Test
    public void test_that_request_url_is_parse_correctly() {
        String request = "\"GET https://cerberus.oss.nike.com:443/dashboard?x=y HTTP/2.0\"";
        String userAgent = "\"User Agent stuff\"";
        String str = "h2 2017-10-02T17:48:24.305799Z app/name-balancer-aaa/bbb 1.1.1.1:17454 1.2.0.6:8443 0.015 0.002 0.000 301 301 242 116 " + request + " " + userAgent + " ECDHE-RSA-AES128-GCM-SHA256 TLSv1.2 arn:aws:elasticloadbalancing:us-west-2:111111:targetgroup/env-https-target-name/00000 \"Root=1-59d27be8-3ef5870d62321261398f1a8c\" \"cerberus.oss.nike.com\" \"arn:aws:iam::0000:server-certificate/group/cerb/env/cms_1111\"";

        ALBAccessLogEvent event = new ALBAccessLogEvent(str);
        assertEquals("443", event.getRequestPort());
        assertEquals("cerberus.oss.nike.com", event.getHostname());
        assertEquals("/dashboard?x=y", event.getRequestUri());
    }


    @Test
    public void testThatIngestLogStreamReturnsAValidListOfEvents() throws IOException {

        handler.setAthenaService(athenaService);
        doReturn(events).when(athenaService).getLogEntrysAfter(any());
        List<ALBAccessLogEvent> events = handler.getLogEvents();

        assertEquals(4, events.size());
    }

    @Test
    public void testThatHandleEventCallsProcessEventsOnTheProcessors() throws IOException {
        String bucketName = "bucketname";

        Processor processor = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);

        handler.overrideProcessors(processors);

        doReturn(null).when(handler).getLogEvents();

        handler.handleScheduledEvent();

        verify(processor, times(1)).processLogEvents(null, logProcessorLambdaConfig, bucketName);
    }


    @Test
    public void testThatHandleEventDoesNotExplodeWhenTheFirstProcessorErrorsOut() throws IOException {
        String bucketName = "bucketname";

        Processor processor = mock(Processor.class);
        Processor processor2 = mock(Processor.class);
        List<Processor> processors = Lists.newLinkedList();
        processors.add(processor);
        doThrow(new RuntimeException("foo")).when(processor).processLogEvents(any(), any(), any());
        processors.add(processor2);

        handler.overrideProcessors(processors);
        doReturn(null).when(handler).getLogEvents();

        handler.handleScheduledEvent();

        verify(processor, times(1)).processLogEvents(null, logProcessorLambdaConfig, bucketName);
        verify(processor2, times(1)).processLogEvents(null, logProcessorLambdaConfig, bucketName);
    }
}
