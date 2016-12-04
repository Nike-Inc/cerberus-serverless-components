package com.nike.cerberus.lambda.waf.processor;

import com.brsanthu.googleanalytics.EventHit;
import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class GoogleAnalyticsKPIProcessorTest {

    private GoogleAnalyticsKPIProcessor processor;
    private List<CloudFrontLogEvent> events = new LinkedList<>();

    @Mock
    GAWrapper tracker;

    @Mock
    CloudFrontLogEvent event;

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
        CloudFrontLogHandlerConfig config = new CloudFrontLogHandlerConfig();
        config.setGoogleAnalyticsId("123");

        processorSpy.processLogEvents(events, config, "foo");
        verify(processorSpy, times(1)).processLogEvents(anyList(), any(GAWrapper.class));
    }

    @Test
    public void testThatServerErrorsGetTracked() {
        when(event.getScStatus()).thenReturn("500");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/who-cares");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("error", "server", "GET /v1/who-cares");
    }

    @Test
    public void testThatBadRequestsGetTracked() {
        when(event.getScStatus()).thenReturn("400");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/who-cares");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("error", "bad request", "GET /v1/who-cares");
    }

    @Test
    public void testThatNone200s400s500sGetTracked() {
        when(event.getScStatus()).thenReturn("301");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/who-cares");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("error", "unknown", "GET /v1/who-cares");
    }

    @Test
    public void testThatUserAuthGetsTrackedV1() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/auth/user");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "user auth", null);
    }

    @Test
    public void testThatUserAuthGetsTrackedV2() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v2/auth/user");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "user auth", null);
    }

    @Test
    public void testThatIAMAuthGetsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/auth/iam-role");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "iam auth", null);
    }

    @Test
    public void testThatVaultNodeReadIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/secret/app/foo/bar");
        when(event.getCsUriQuery()).thenReturn("-");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node read", "foo", "bar");
    }

    @Test
    public void testThatVaultNodeReadListIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/secret/app/foo/");
        when(event.getCsUriQuery()).thenReturn("?list=true");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node read", "foo", "/?list=true");
    }

    @Test
    public void testThatVaultNodeWriteIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("POST");
        when(event.getCsUriStem()).thenReturn("/v1/secret/app/foo/bar");
        when(event.getCsUriQuery()).thenReturn("-");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node write", "foo", "bar");
    }

    @Test
    public void testThatVaultNodeDeleteIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("DELETE");
        when(event.getCsUriStem()).thenReturn("/v1/secret/app/foo/bar");
        when(event.getCsUriQuery()).thenReturn("-");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("sdb node delete", "foo", "bar");
    }

    @Test
    public void testThatSDBCreationIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("POST");
        when(event.getCsUriStem()).thenReturn("/v1/safe-deposit-box");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb created", null);
    }

    @Test
    public void testThatSDBReadIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("GET");
        when(event.getCsUriStem()).thenReturn("/v1/safe-deposit-box/foo-bar-bam");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb read", null);
    }

    @Test
    public void testThatSDBUpdateIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("PUT");
        when(event.getCsUriStem()).thenReturn("/v1/safe-deposit-box/foo-bar-bam");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb update", null);
    }

    @Test
    public void testThatSDBDeleteIsTracked() {
        when(event.getScStatus()).thenReturn("200");
        when(event.getCsMethod()).thenReturn("DELETE");
        when(event.getCsUriStem()).thenReturn("/v1/safe-deposit-box/foo-bar-bam");

        processor.processLogEvents(events, tracker);

        verify(tracker).trackEvent("cms", "sdb delete", null);
    }
}
