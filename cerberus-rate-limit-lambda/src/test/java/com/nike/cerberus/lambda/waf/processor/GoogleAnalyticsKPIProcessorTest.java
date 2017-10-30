package com.nike.cerberus.lambda.waf.processor;

import com.nike.cerberus.lambda.waf.AppLoadBalancerLogEvent;
import com.nike.cerberus.lambda.waf.RateLimitConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class GoogleAnalyticsKPIProcessorTest {

    private GoogleAnalyticsKPIProcessor processor;
    private List<AppLoadBalancerLogEvent> events = new LinkedList<>();

    @Mock
    GAWrapper tracker;

    @Mock
    AppLoadBalancerLogEvent event;

    @Before
    public void before() {
        initMocks(this);

        events.add(event);

        processor = new GoogleAnalyticsKPIProcessor();
    }

    @Test
    public void testThatTheDefaultProccessLogEventsCreatesAGA() {
        GoogleAnalyticsKPIProcessor processorSpy = spy(processor);
        doNothing().when(processorSpy).processLogEvents(anyList(), any(GAWrapper.class));
        RateLimitConfig config = mock(RateLimitConfig.class);
        when(config.getGoogleAnalyticsId()).thenReturn("123");

        processorSpy.processLogEvents(events, config, "foo");
        verify(processorSpy, times(1)).processLogEvents(anyList(), any(GAWrapper.class));
    }

    @Test
    public void testThatServerErrorsGetTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("500");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/who-cares");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("error", "server", "GET /v1/who-cares");
    }

    @Test
    public void testThatBadRequestsGetTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("400");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/who-cares");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("error", "bad request", "GET /v1/who-cares");
    }

    @Test
    public void testThatNone200s400s500sGetTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("301");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/who-cares");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("error", "unknown", "GET 301 /v1/who-cares");
    }

    @Test
    public void testThatUserAuthGetsTrackedV1() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/auth/user");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "user auth", null);
    }

    @Test
    public void testThatUserAuthGetsTrackedV2() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v2/auth/user");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "user auth", null);
    }

    @Test
    public void testThatIAMAuthGetsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/auth/iam-role");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "iam auth", null);
    }

    @Test
    public void testThatVaultNodeReadIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/secret/app/foo/bar");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node read", "foo", "bar");
    }

    @Test
    public void testThatVaultNodeReadListIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/secret/app/foo/?list=true");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node read", "foo", "/?list=true");
    }

    @Test
    public void testThatVaultNodeWriteIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("POST");
        when(event.getRequestUri()).thenReturn("/v1/secret/app/foo/bar");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node write", "foo", "bar");
    }

    @Test
    public void testThatVaultNodeDeleteIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("DELETE");
        when(event.getRequestUri()).thenReturn("/v1/secret/app/foo/bar");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node delete", "foo", "bar");
    }

    @Test
    public void testThatSDBCreationIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("POST");
        when(event.getRequestUri()).thenReturn("/v1/safe-deposit-box");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb created", null);
    }

    @Test
    public void testThatSDBReadIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("GET");
        when(event.getRequestUri()).thenReturn("/v1/safe-deposit-box/foo-bar-bam");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb read", null);
    }

    @Test
    public void testThatSDBUpdateIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("PUT");
        when(event.getRequestUri()).thenReturn("/v1/safe-deposit-box/foo-bar-bam");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb update", null);
    }

    @Test
    public void testThatSDBDeleteIsTracked() {
        when(event.getLoadBalancerStatusCode()).thenReturn("200");
        when(event.getHttpMethod()).thenReturn("DELETE");
        when(event.getRequestUri()).thenReturn("/v1/safe-deposit-box/foo-bar-bam");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb delete", null);
    }
}
