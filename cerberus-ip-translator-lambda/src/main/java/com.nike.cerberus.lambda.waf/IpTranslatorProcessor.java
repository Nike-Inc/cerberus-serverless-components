/*
 * Copyright (c) 2019 Nike Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.fieldju.commons.EnvUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpTranslatorProcessor {

    private CerberusMetadataLookup cerberusMetadataLookup = new CerberusMetadataLookup();
    private SlackMessageSender slackMessageSender = new SlackMessageSender();
    private static String cerberusUrl = EnvUtils.getRequiredEnv("CERBERUS_URL");
    private static String environment;

    public void translateIpToMetadata(SlackMessage message) {

        List<String> parsedIps = processSlackMessage(message);

        if (parsedIps.isEmpty()) return;

        parsedIps.forEach(ip -> {
            List<Map<String,String>> ipMetadataTable = parseAndTranslateIpAddressToMetadata(ip, environment);
            slackMessageSender.createAndSendSlackMessage(ip, ipMetadataTable);
        });
    }

    private List<String> processSlackMessage(SlackMessage message) {
        String text = message.getText();
        List<String> parsedIps = new LinkedList<>();

        Pattern rateLimitProcessor = Pattern.compile(".*Rate Limiting Processor run summary\n");

        if (! rateLimitProcessor.matcher(text).find()) {
            return parsedIps;
        }

        Pattern envName = Pattern.compile(".*Environment: (?<env>.*?)\n");
        Matcher envMatcher = envName.matcher(text);
        if (! envMatcher.find()) {
            return parsedIps;
        }

        environment = envMatcher.group("env");

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

        return parsedIps;
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

        ArrayList<Map<String, String>> sdbMetadata = cerberusMetadataLookup.getCerberusMetadata(cerberusUrl);
        for (Map<String, String> row : ipMetadataTable) {
            ArrayList<String> owner = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, row.get("sdbName"), row.get("principalName"));
            row.put("owner", String.join("\n", owner));
        }

        return ipMetadataTable;
    }
}