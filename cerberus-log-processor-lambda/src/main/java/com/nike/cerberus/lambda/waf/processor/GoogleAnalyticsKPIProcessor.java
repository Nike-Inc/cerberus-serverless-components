package com.nike.cerberus.lambda.waf.processor;

import com.brsanthu.googleanalytics.EventHit;
import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.nike.cerberus.lambda.waf.ALBAccessLogEvent;
import com.nike.cerberus.lambda.waf.LogProcessorLambdaConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Optional Processor for people that would like to keep track of KPIs that can be derived from their access Logs.
 */
public class GoogleAnalyticsKPIProcessor implements Processor {

    private final Logger log = Logger.getLogger(getClass());

    private GAWrapper tracker;

    @Override
    public void processLogEvents(List<ALBAccessLogEvent> events, LogProcessorLambdaConfig config, String bucketName) {
        String trackingId = config.getGoogleAnalyticsId();
        if (StringUtils.isBlank(trackingId)) {
            return;
        }

        processLogEvents(events, new GAWrapper(trackingId));
    }

    /**
     * Exposed for testing so that a Mock Google Analytics may be supplied
     */
    protected void processLogEvents(List<ALBAccessLogEvent> events, GAWrapper tracker) {

        this.tracker = tracker;

        events.forEach(event -> {
            String start = event.getLoadBalancerStatusCode().substring(0, 1);
            if ("5".equals(start)) {
                trackServerError(event);
                return;
            } else if ("4".equals(start)) {
                trackBadRequest(event);
                return;
            } else if ("2".equals(start)) {
                String path = event.getRequestUri();

                if (path.matches("/v\\d/auth/user")) {
                    trackUserAuth(event);
                    return;
                }

                if (path.matches("/v\\d/auth/iam-role")) {
                    trackIAMAuth(event);
                    return;
                }

                if (path.matches("/v\\d/secret/.*")) {
                    trackSDBNodeEvent(event);
                    return;
                }

                if (path.matches("/v\\d/safe-deposit-box") && StringUtils.equals(event.getHttpMethod(), "POST")) {
                    trackSDBCreated(event);
                    return;
                }

                if (path.matches("/v\\d/safe-deposit-box/.*")) {
                    trackSDBEvent(event);
                    return;
                }

            } else {
                trackUnknownStatus(event);
                return;
            }
        });
    }

    /**
     * Track when an SDB is created.
     */
    private void trackSDBCreated(ALBAccessLogEvent event) {
        String eventCategory = "cms";
        String eventAction = "sdb created";
        String eventLabel = null;

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track when an SDB in CMS is read, updated or deleted
     */
    private void trackSDBEvent(ALBAccessLogEvent event) {
        String eventCategory = "cms";
        String path = event.getRequestUri();
        String[] parts = path.split("/");

        String eventAction;
        switch (event.getHttpMethod()) {
            case "GET":
                eventAction = "sdb read";
                break;
            case "PUT":
                eventAction = "sdb update";
                break;
            case "DELETE":
                eventAction = "sdb delete";
                break;
            default:
                eventAction = event.getHttpMethod();
                break;
        }

        tracker.trackEvent(eventCategory, eventAction, null);
    }

    /**
     * Track when a node in vault for an SDB is written, read or deleted.
     */
    private void trackSDBNodeEvent(ALBAccessLogEvent event) {
        String path = event.getRequestUri();
        String[] parts = path.split("/");

        // Set the action to the SDB Name
        String eventAction = parts[4];
        // Set the label to the node
        String eventLabel;
        if (parts.length < 6) {
            eventLabel = "/";
        } else if (path.matches(".*?.*=.*")){
            eventLabel = StringUtils.prependIfMissing(parts[parts.length-1], "/");
        } else {
            eventLabel = StringUtils.substringAfterLast(StringUtils.removeEnd(path, "/"), "/");
        }

        String eventCategory;
        switch (event.getHttpMethod()) {
            case "GET":
                eventCategory = "sdb node read";
                break;
            case "POST":
                eventCategory = "sdb node write";
                break;
            case "DELETE":
                eventCategory = "sdb node delete";
                break;
            default:
                eventCategory = "sdb node " + event.getHttpMethod();
                break;
        }

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track IAM Auth Events.
     */
    private void trackIAMAuth(ALBAccessLogEvent event) {
        String eventCategory = "cms";
        String eventAction = "iam auth";
        String eventLabel = null;

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track User Auth Events.
     */
    private void trackUserAuth(ALBAccessLogEvent event) {
        String eventCategory = "cms";
        String eventAction = "user auth";
        String eventLabel = null;

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track bad requests.
     */
    private void trackBadRequest(ALBAccessLogEvent event) {
        String eventCategory = "error";
        String eventAction = "bad request";
        String eventLabel = String.format("%s %s", event.getHttpMethod(), event.getRequestUri());

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track server errors.
     */
    private void trackServerError(ALBAccessLogEvent event) {
        String eventCategory = "error";
        String eventAction = "server";
        String eventLabel = String.format("%s %s", event.getHttpMethod(), event.getRequestUri());

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track other status codes that may happen.
     */
    private void trackUnknownStatus(ALBAccessLogEvent event) {
        String eventCategory = "error";
        String eventAction = "unknown";
        String eventLabel = String.format("%s %s %s", event.getHttpMethod(), event.getLoadBalancerStatusCode(), event.getRequestUri());

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }
}

class GAWrapper {

    private GoogleAnalytics ga;

    public GAWrapper(String trackingId) {
        ga = new GoogleAnalytics(trackingId);
    }

    protected void trackEvent(String eventCategory, String eventAction, String eventLabel) {
        ga.post(new EventHit().eventCategory(eventCategory).eventAction(eventAction).eventLabel(eventLabel));
    }
}
