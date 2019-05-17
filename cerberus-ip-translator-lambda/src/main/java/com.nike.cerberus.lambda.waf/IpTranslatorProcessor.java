/*
 * Copyright 2019 Nike, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus.lambda.waf;

import com.amazonaws.services.athena.model.Datum;
import com.amazonaws.services.athena.model.ResultSet;
import org.apache.commons.lang3.StringUtils;

import org.apache.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IpTranslatorProcessor {

    Logger log = Logger.getLogger(getClass());

    private CerberusMetadataLookup cerberusMetadataLookup = new CerberusMetadataLookup();
    private SlackMessageSender slackMessageSender = new SlackMessageSender();

    public void processMessageIfFromRateLimiter(SlackMessage message) {
        log.info("processMessageIfFromRateLimiter called with the following message: " + message.getText());

        if (! isMessageFromRateLimiter(message)) {
            log.info("Slack message was not from rate limiter message, aborting...");
            return;
        }

        String environment = getEnvironmentFromSlackMessage(message);

        translateIpToMetadata(message, environment);
    }

    protected boolean isMessageFromRateLimiter(SlackMessage message) {
        return message.getText().startsWith("ALB Log Event Handler - Rate Limiting Processor run summary");
    }

    protected String getEnvironmentFromSlackMessage(SlackMessage message) {
        Pattern envName = Pattern.compile(".*Environment: (?<env>.*?)\\n");
        Matcher envMatcher = envName.matcher(message.getText());
        if (! envMatcher.find()) {
            log.info("Failed to determine environment from slack message, aborting...");
            throw new RuntimeException("Failed to determine environment!");
        }
        return envMatcher.group("env");
    }

    private void translateIpToMetadata(SlackMessage message, String environment) {

        List<String> parsedIps = getIpsFromSlackMessage(message);

        log.info("Parsed the following ips: " + String.join(", ", parsedIps));

        if (parsedIps.isEmpty()) {
            log.info("There were no ips parsed, aborting...");
            return;
        }

        parsedIps.forEach(ip -> {
            List<Map<String,String>> ipMetadataTable = parseAndTranslateIpAddressToMetadata(ip, environment);
            slackMessageSender.createAndSendSlackMessage(ip, ipMetadataTable);
        });
    }

    protected List<String> getIpsFromSlackMessage(SlackMessage message) {
        String text = message.getText();
        List<String> parsedIps = new LinkedList<>();

        Arrays.stream(text.split("\\n"))
                .filter(i -> i.startsWith("IP addresses added to auto block list:"))
                .findFirst().ifPresent(ipAddedLine -> {
            ipAddedLine = StringUtils.removeStart(ipAddedLine, "IP addresses added to auto block list: ");
            ipAddedLine = StringUtils.removeEnd(ipAddedLine.trim(), ",");
            String[] ips = ipAddedLine.split(",");
            Arrays.stream(ips).forEach(ipLine -> {
                parsedIps.add(ipLine.trim().split("\\s")[0]);
            });
        });

        return parsedIps.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
    }

    private List<Map<String,String>> parseAndTranslateIpAddressToMetadata(String ipAddress, String environment) {

        AthenaQuery athenaQuery = new AthenaQuery();

        List<Map<String,String>> ipMetadataTable = new ArrayList<Map<String,String>>();

        ResultSet result = athenaQuery.processIpAddressInAthena(ipAddress, environment);

        result.getRows().stream().skip(1).forEach(row -> {
            Map<String,String> temp = new HashMap<>();
            Iterator<Datum> iter = row.getData().stream().iterator();

            temp.put("principalName", iter.next().getVarCharValue());
            temp.put("action", iter.next().getVarCharValue());
            temp.put("sdbName", iter.next().getVarCharValue());
            temp.put("clientVersion", iter.next().getVarCharValue());
            temp.put("count", iter.next().getVarCharValue());

            ipMetadataTable.add(temp);
        });

        ArrayList<Map<String, String>> sdbMetadata = cerberusMetadataLookup.getCerberusMetadata(environment);
        for (Map<String, String> row : ipMetadataTable) {
            ArrayList<String> owner = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, row.get("sdbName"), row.get("principalName"));
            row.put("owner", String.join("\n", owner));
        }

        return ipMetadataTable;
    }
}
