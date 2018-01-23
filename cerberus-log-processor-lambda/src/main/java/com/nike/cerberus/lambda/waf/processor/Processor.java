package com.nike.cerberus.lambda.waf.processor;

import com.nike.cerberus.lambda.waf.ALBAccessLogEvent;
import com.nike.cerberus.lambda.waf.LogProcessorLambdaConfig;

import java.util.List;

public interface Processor {
    void processLogEvents(List<ALBAccessLogEvent> events, LogProcessorLambdaConfig config, String bucketName);
}
