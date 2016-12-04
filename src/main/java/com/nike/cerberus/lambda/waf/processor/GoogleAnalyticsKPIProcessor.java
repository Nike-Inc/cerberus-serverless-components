package com.nike.cerberus.lambda.waf.processor;

import com.brsanthu.googleanalytics.EventHit;
import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Optional Processor for people that would like to keep track of KPIs that can be derived from CloudFront Gateway Logs.
 */
public class GoogleAnalyticsKPIProcessor implements Processor {

    private GAWrapper tracker;

    @Override
    public void processLogEvents(List<CloudFrontLogEvent> events, CloudFrontLogHandlerConfig config, String bucketName) {
        String trackingId = config.getGoogleAnalyticsId();
        if (StringUtils.isBlank(trackingId)) {
            return;
        }

        processLogEvents(events, new GAWrapper(trackingId));
    }

    /**
     * Exposed for testing so that a Mock Google Analytics may be supplied
     */
    protected void processLogEvents(List<CloudFrontLogEvent> events, GAWrapper tracker) {

        this.tracker = tracker;

        events.forEach(event -> {
            String start = event.getScStatus().substring(0, 1);
            if ("5".equals(start)) {
                trackServerError(event);
                return;
            } else if ("4".equals(start)) {
                trackBadRequest(event);
                return;
            } else if ("2".equals(start)) {
                String path = event.getCsUriStem();

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

                if (path.matches("/v\\d/safe-deposit-box") && StringUtils.equals(event.getCsMethod(), "POST")) {
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
    private void trackSDBCreated(CloudFrontLogEvent event) {
        String eventCategory = "cms";
        String eventAction = "sdb created";
        String eventLabel = null;

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track when an SDB in CMS is read, updated or deleted
     */
    private void trackSDBEvent(CloudFrontLogEvent event) {
        String eventCategory = "cms";
        String path = event.getCsUriStem();
        String[] parts = path.split("/");

        String eventAction;
        switch (event.getCsMethod()) {
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
                eventAction = event.getCsMethod();
                break;
        }

        tracker.trackEvent(eventCategory, eventAction, null);
    }

    /**
     * Track when a node in vault for an SDB is written, read or deleted.
     */
    private void trackSDBNodeEvent(CloudFrontLogEvent event) {
        String path = event.getCsUriStem();
        String[] parts = path.split("/");

        // Set the action to the SDB Name
        String eventAction = parts[4];
        // Set the label to the node
        String eventLabel;
        String query = event.getCsUriQuery();
        if (parts.length < 6) {
            eventLabel = "/";
        } else {
            eventLabel = StringUtils.join(Arrays.copyOfRange(parts, 5, parts.length), "/");
        }

        eventLabel += !StringUtils.equals(query, "-") ? query : "";

        String eventCategory;
        switch (event.getCsMethod()) {
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
                eventCategory = "sdb node " + event.getCsMethod();
                break;
        }

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track IAM Auth Events.
     */
    private void trackIAMAuth(CloudFrontLogEvent event) {
        String eventCategory = "cms";
        String eventAction = "iam auth";
        String eventLabel = null;

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track User Auth Events.
     */
    private void trackUserAuth(CloudFrontLogEvent event) {
        String eventCategory = "cms";
        String eventAction = "user auth";
        String eventLabel = null;

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track bad requests.
     */
    private void trackBadRequest(CloudFrontLogEvent event) {
        String eventCategory = "error";
        String eventAction = "bad request";
        String eventLabel = String.format("%s %s", event.getCsMethod(), event.getCsUriStem());

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track server errors.
     */
    private void trackServerError(CloudFrontLogEvent event) {
        String eventCategory = "error";
        String eventAction = "server";
        String eventLabel = String.format("%s %s", event.getCsMethod(), event.getCsUriStem());

        tracker.trackEvent(eventCategory, eventAction, eventLabel);
    }

    /**
     * Track other status codes that may happen.
     */
    private void trackUnknownStatus(CloudFrontLogEvent event) {
        String eventCategory = "error";
        String eventAction = "unknown";
        String eventLabel = String.format("%s %s %s", event.getCsMethod(), event.getScStatus(), event.getCsUriStem());

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
