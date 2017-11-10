package com.nike.cerberus.lambda.waf.processor;

import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;

import java.util.List;

public interface Processor {
    void processLogEvents(List<CloudFrontLogEvent> events, CloudFrontLogHandlerConfig config, String bucketName);
}
