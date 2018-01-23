package com.nike.cerberus.lambda.waf.processor;

import com.google.common.collect.Sets;
import com.nike.cerberus.lambda.waf.ALBAccessLogEvent;
import com.nike.cerberus.lambda.waf.LogProcessorLambdaConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * For tracking down clients that are not using TLS1.2
 */
public class TlsVerificationProcessor implements Processor {

    private static final String TLS_1_2 = "TLSv1.2";
    private static final String NON_TLS = "-";
    private static final Set<String> ACCEPTABLE_TLS_VERSIONS = Sets.newHashSet(
            TLS_1_2,
            // non-TLS isn't really acceptable but it can be ignored since it can never reach the system
            NON_TLS
    );

    /**
     * List of paths to ignore
     */
    private static final Set<String> SUPPRESSED_PATHS = Sets.newHashSet(split(trimToEmpty(System.getenv("TLS_VERIFICATION_SUPPRESSED_PATHS")), ","));

    private final Logger log = Logger.getLogger(getClass());

    @Override
    public void processLogEvents(List<ALBAccessLogEvent> events, LogProcessorLambdaConfig config, String bucketName) {
        List<ALBAccessLogEvent> nonAcceptableEvents = new LinkedList<>();
        events.forEach(event -> {
            if (!ACCEPTABLE_TLS_VERSIONS.contains(event.getSslProtocol())
                    && !SUPPRESSED_PATHS.contains(event.getRequestUri())) {
                nonAcceptableEvents.add(event);
            }
        });

        if (nonAcceptableEvents.isEmpty()) {
            log.info("No requests found with TLS versions not in acceptable version list");
            return;
        }

        StringBuilder sb = new StringBuilder("Cloud Front Log Event Handler - TLS Verification Processor run summary");
        sb.append('\n').append("Running Environment: ").append(config.getEnv()).append('\n');
        sb.append('\n').append("Ignoring Paths: " + SUPPRESSED_PATHS).append('\n');
        nonAcceptableEvents.forEach(event -> {
            sb
                    .append("TLS Version: ").append(event.getSslProtocol())
                    .append(", Path: ").append(event.getRequestUri())
                    .append(", IP: ").append(event.getRequestingClientIp())
                    .append(", User Agent: ").append(StringUtils.substring(event.getUserAgent(), 0, 30))
                    .append('\n');
        });

        String msg = sb.toString();

        SlackUtils.logMsgIfEnabled(msg, config, "Tls-Verification-Processor");

        log.info(msg);
    }
}
