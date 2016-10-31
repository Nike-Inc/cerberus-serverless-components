package com.nike.cerberus.lambda.waf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudFrontLogHandlerConfig {

    private String manualWhiteListIpSetId;

    private String manualBlackListIpSetId;

    private String rateLimitAutoBlackListIpSetId;

    private Integer rateLimitViolationBlacklistPeriodInMinutes;

    private Integer requestPerMinuteLimit;

    private String slackWebHookUrl;

    private String slackIcon = ":wolf:";

    private String env;

    public String getManualWhiteListIpSetId() {
        return manualWhiteListIpSetId;
    }

    public void setManualWhiteListIpSetId(String manualWhiteListIpSetId) {
        this.manualWhiteListIpSetId = manualWhiteListIpSetId;
    }

    public String getManualBlackListIpSetId() {
        return manualBlackListIpSetId;
    }

    public void setManualBlackListIpSetId(String manualBlackListIpSetId) {
        this.manualBlackListIpSetId = manualBlackListIpSetId;
    }

    public String getRateLimitAutoBlackListIpSetId() {
        return rateLimitAutoBlackListIpSetId;
    }

    public void setRateLimitAutoBlackListIpSetId(String rateLimitAutoBlackListIpSetId) {
        this.rateLimitAutoBlackListIpSetId = rateLimitAutoBlackListIpSetId;
    }

    public Integer getRateLimitViolationBlacklistPeriodInMinutes() {
        return rateLimitViolationBlacklistPeriodInMinutes;
    }

    public void setRateLimitViolationBlacklistPeriodInMinutes(Integer rateLimitViolationBlacklistPeriodInMinutes) {
        this.rateLimitViolationBlacklistPeriodInMinutes = rateLimitViolationBlacklistPeriodInMinutes;
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

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }
}
