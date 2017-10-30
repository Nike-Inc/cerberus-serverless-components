package com.nike.cerberus.lambda.waf.processor;

import com.nike.cerberus.lambda.waf.AppLoadBalancerLogEvent;
import com.nike.cerberus.lambda.waf.RateLimitConfig;

import java.util.List;

public interface Processor {
    void processLogEvents(List<AppLoadBalancerLogEvent> events, RateLimitConfig params, String bucketName);
}
