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

import com.fieldju.commons.StringUtils;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class Handler {

    private final Logger log = Logger.getLogger(getClass());

    private final String apiToken = System.getenv("SLACK_TOKEN");

    private final IpTranslatorProcessor ipTranslatorProcessor;

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public Handler() {
        ipTranslatorProcessor = new IpTranslatorProcessor();
    }

    public void handleSlackOutgoingWebHookEvent(Map<String, Object> data) {
        SlackMessage slackMessage = getMessageFromData(data);

        if (StringUtils.isNotBlank(apiToken) && ! StringUtils.equals(apiToken, slackMessage.getToken())) {
            throw new RuntimeException("Error the provided token did not match the expected token");
        }

        ipTranslatorProcessor.translateIpToMetadata(slackMessage);
    }

    private SlackMessage getMessageFromData(Map<String, Object> data) {
        String[] kvPairs = ((String)data.get("body")).split("&");
        Map<String, String> slackData = Arrays.stream(kvPairs)
                .collect(Collectors.toMap(
                        kv -> kv.split("=")[0],
                        kv -> kv.split("=")[1]));
        JsonElement jsonElement = gson.toJsonTree(slackData);
        return gson.fromJson(jsonElement, SlackMessage.class);
    }
}

