package com.nike.cerberus.lambda.waf.processor;

import com.google.common.collect.ImmutableList;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

/**
 * For tracking down clients that are not using TLS1.2
 */
public class TlsVerificationProcessor implements Processor {

    private static final String TLS_1_2 = "TLSv1.2";
    private static final List<String> ACCEPTABLE_TLS_VERSIONS = ImmutableList.of(
            TLS_1_2
    );

    private final Logger log = Logger.getLogger(getClass());

    @Override
    public void processLogEvents(List<CloudFrontLogEvent> events, CloudFrontLogHandlerConfig config, String bucketName) {
        List<CloudFrontLogEvent> nonAcceptableEvents = new LinkedList<>();
        events.forEach(event -> {
            if ( ! ACCEPTABLE_TLS_VERSIONS.contains(event.getSslProtocol()) ) {
                nonAcceptableEvents.add(event);
            }
        });

        if (nonAcceptableEvents.isEmpty()) {
            log.info("No requests found with TLS versions not in acceptable version list");
            return;
        }

        StringBuilder sb = new StringBuilder("Cloud Front Log Event Handler - TLS Verification Processor run summary");
        sb.append('\n').append("Running Environment: ").append(config.getEnv()).append('\n');
        nonAcceptableEvents.forEach(event -> {
            sb.append("IP: ").append(event.getCIp()).append('\n')
                    .append("User Agent: ").append(StringUtils.substring(event.getcsUserAgent(), 0, 30)).append('\n')
                    .append("Path: ").append(event.getCsUriStem()).append('\n')
                    .append("TLS Version: ").append(event.getSslProtocol()).append('\n').append('\n');
        });

        String msg = sb.toString();

        SlackUtils.logMsgIfEnabled(msg, config, "Tls-Verification-Processor");

        log.info(msg);
    }
}
