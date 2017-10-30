package com.nike.cerberus.lambda.waf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fieldju.commons.EnvUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RateLimitConfig {

    private String manualWhitelistIpSetId;

    private String manualBlacklistIpSetId;

    private String rateLimitAutoBlacklistIpSetId;

    private Integer blacklistDurationInMinutes;

    private Integer requestPerMinuteLimit;

    private String slackWebHookUrl;

    private String slackIcon;

    private String googleAnalyticsId;

    private String env;

    public RateLimitConfig(String env, String manualWhitelistIpSetId, String manualBlacklistIpSetId,
                           String rateLimitAutoBlacklistIpSetId, Integer blacklistDurationInMinutes,
                           Integer requestPerMinuteLimit, String slackWebHookUrl, String slackIcon,
                           String googleAnalyticsId) {
        this.env = env;
        this.manualWhitelistIpSetId = manualWhitelistIpSetId;
        this.manualBlacklistIpSetId = manualBlacklistIpSetId;
        this.rateLimitAutoBlacklistIpSetId = rateLimitAutoBlacklistIpSetId;
        this.blacklistDurationInMinutes = blacklistDurationInMinutes;
        this.requestPerMinuteLimit = requestPerMinuteLimit;
        this.slackWebHookUrl = slackWebHookUrl;
        this.slackIcon = slackIcon;
        this.googleAnalyticsId = googleAnalyticsId;
    }

    public RateLimitConfig() {
        env = EnvUtils.getRequiredEnv("ENVIRONMENT");
        manualBlacklistIpSetId = EnvUtils.getRequiredEnv("MANUAL_BLACKLIST_IP_SET_ID");
        manualWhitelistIpSetId = EnvUtils.getRequiredEnv("MANUAL_WHITELIST_IP_SET_ID");
        rateLimitAutoBlacklistIpSetId = EnvUtils.getRequiredEnv("RATE_LIMIT_AUTO_BLACKLIST_IP_SET_ID");
        blacklistDurationInMinutes = Integer.parseInt(
                EnvUtils.getEnvWithDefault("VIOLATION_BLACKLIST_DURATION_IN_MINS", "60"));
        requestPerMinuteLimit = Integer.parseInt(
                EnvUtils.getEnvWithDefault("REQUEST_PER_MIN_LIMIT", "100"));
        slackIcon = EnvUtils.getEnvWithDefault("SLACK_ICON", ":wolf:");
        slackWebHookUrl = EnvUtils.getEnvWithDefault("SLACK_WEB_HOOK_URL", null);
        googleAnalyticsId = EnvUtils.getEnvWithDefault("GOOGLE_ANALYTICS_ID", null);
    }

    public String getManualWhitelistIpSetId() {
        return manualWhitelistIpSetId;
    }

    public void setManualWhitelistIpSetId(String manualWhitelistIpSetId) {
        this.manualWhitelistIpSetId = manualWhitelistIpSetId;
    }

    public String getManualBlacklistIpSetId() {
        return manualBlacklistIpSetId;
    }

    public void setManualBlacklistIpSetId(String manualBlacklistIpSetId) {
        this.manualBlacklistIpSetId = manualBlacklistIpSetId;
    }

    public String getRateLimitAutoBlacklistIpSetId() {
        return rateLimitAutoBlacklistIpSetId;
    }

    public void setRateLimitAutoBlacklistIpSetId(String rateLimitAutoBlacklistIpSetId) {
        this.rateLimitAutoBlacklistIpSetId = rateLimitAutoBlacklistIpSetId;
    }

    public Integer getBlacklistDurationInMinutes() {
        return blacklistDurationInMinutes;
    }

    public void setBlacklistDurationInMinutes(Integer blacklistDurationInMinutes) {
        this.blacklistDurationInMinutes = blacklistDurationInMinutes;
    }

    public Integer getRequestPerMinuteLimit() {
        return requestPerMinuteLimit;
    }

    public void setRequestPerMinuteLimit(Integer requestPerMinuteLimit) {
        this.requestPerMinuteLimit = requestPerMinuteLimit;
    }

    public String getSlackWebHookUrl() {
        return slackWebHookUrl;
    }

    public void setSlackWebHookUrl(String slackWebHookUrl) {
        this.slackWebHookUrl = slackWebHookUrl;
    }

    public String getSlackIcon() {
        return slackIcon;
    }

    public void setSlackIcon(String slackIcon) {
        this.slackIcon = slackIcon;
    }

    public String getGoogleAnalyticsId() {
        return googleAnalyticsId;
    }

    public void setGoogleAnalyticsId(String googleAnalyticsId) {
        this.googleAnalyticsId = googleAnalyticsId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }
}
