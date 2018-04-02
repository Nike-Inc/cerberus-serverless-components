package com.nike.cerberus.lambda.waf;

import com.amazonaws.regions.Regions;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fieldju.commons.EnvUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogProcessorLambdaConfig {

    private String manualWhitelistIpSetId;

    private String manualBlacklistIpSetId;

    private String rateLimitAutoBlacklistIpSetId;

    private Integer blacklistDurationInMinutes;

    private Integer requestPerHourLimit;

    private String slackWebHookUrl;

    private String slackIcon;

    private String env;

    private String athenaDatabaseName;

    private String athenaTableName;

    private String athenaQueryResultBucketName;

    private String albLogBucketName;

    private String iamPrincipalArn;

    private Regions region;

    public LogProcessorLambdaConfig(String env, String manualWhitelistIpSetId, String manualBlacklistIpSetId,
                                    String rateLimitAutoBlacklistIpSetId, Integer blacklistDurationInMinutes,
                                    Integer requestPerHourLimit, String slackWebHookUrl, String slackIcon,
                                    String athenaDatabaseName, String athenaTableName,
                                    String athenaQueryResultBucketName, String albLogBucketName,
                                    String iamPrincipalArn, Regions region) {
        this.env = env;
        this.manualWhitelistIpSetId = manualWhitelistIpSetId;
        this.manualBlacklistIpSetId = manualBlacklistIpSetId;
        this.rateLimitAutoBlacklistIpSetId = rateLimitAutoBlacklistIpSetId;
        this.blacklistDurationInMinutes = blacklistDurationInMinutes;
        this.requestPerHourLimit = requestPerHourLimit;
        this.slackWebHookUrl = slackWebHookUrl;
        this.slackIcon = slackIcon;
        this.athenaDatabaseName = athenaDatabaseName;
        this.athenaTableName = athenaTableName;
        this.athenaQueryResultBucketName = athenaQueryResultBucketName;
        this.albLogBucketName = albLogBucketName;
        this.iamPrincipalArn = iamPrincipalArn;
        this.region = region;
    }

    public LogProcessorLambdaConfig() {
        env = EnvUtils.getRequiredEnv("ENVIRONMENT");
        manualBlacklistIpSetId = EnvUtils.getRequiredEnv("MANUAL_BLACKLIST_IP_SET_ID");
        manualWhitelistIpSetId = EnvUtils.getRequiredEnv("MANUAL_WHITELIST_IP_SET_ID");
        rateLimitAutoBlacklistIpSetId = EnvUtils.getRequiredEnv("RATE_LIMIT_AUTO_BLACKLIST_IP_SET_ID");
        blacklistDurationInMinutes = Integer.parseInt(
                EnvUtils.getEnvWithDefault("VIOLATION_BLACKLIST_DURATION_IN_MINS", "60"));
        requestPerHourLimit = Integer.parseInt(
                EnvUtils.getEnvWithDefault("REQUEST_PER_HOUR_LIMIT", "100"));
        slackIcon = EnvUtils.getEnvWithDefault("SLACK_ICON", ":wolf:");
        slackWebHookUrl = EnvUtils.getEnvWithDefault("SLACK_WEB_HOOK_URL", null);
        athenaDatabaseName = EnvUtils.getRequiredEnv("ATHENA_DATABASE_NAME");
        athenaTableName = EnvUtils.getEnvWithDefault("ATHENA_TABLE_NAME", "alb_logs");
        athenaQueryResultBucketName = EnvUtils.getRequiredEnv("ATHENA_QUERY_RESULT_BUCKET_NAME");
        albLogBucketName = EnvUtils.getRequiredEnv("ALB_LOG_BUCKET");
        iamPrincipalArn = EnvUtils.getRequiredEnv("IAM_PRINCIPAL_ARN");
        region = Regions.fromName(EnvUtils.getEnvWithDefault("REGION", "us-west-2"));
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

    public Integer getRequestPerHourLimit() {
        return requestPerHourLimit;
    }

    public void setRequestPerHourLimit(Integer requestPerHourLimit) {
        this.requestPerHourLimit = requestPerHourLimit;
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

    public String getAthenaDatabaseName() {
        return athenaDatabaseName;
    }

    public void setAthenaDatabaseName(String athenaDatabaseName) {
        this.athenaDatabaseName = athenaDatabaseName;
    }

    public String getAthenaQueryResultBucketName() {
        return athenaQueryResultBucketName;
    }

    public void setAthenaQueryResultBucketName(String athenaQueryResultBucketName) {
        this.athenaQueryResultBucketName = athenaQueryResultBucketName;
    }

    public String getAlbLogBucketName() {
        return albLogBucketName;
    }

    public void setAlbLogBucketName(String albLogBucketName) {
        this.albLogBucketName = albLogBucketName;
    }

    public String getAthenaTableName() {
        return athenaTableName;
    }

    public void setAthenaTableName(String athenaTableName) {
        this.athenaTableName = athenaTableName;
    }

    public String getIamPrincipalArn() {
        return iamPrincipalArn;
    }

    public void setIamPrincipalArn(String iamPrincipalArn) {
        this.iamPrincipalArn = iamPrincipalArn;
    }

    public Regions getRegion() {
        return region;
    }

    public void setRegion(Regions region) {
        this.region = region;
    }
}
