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

import com.fieldju.slackclient.SlackClient;
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.model.block.LayoutBlock;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public class SlackMessageSender {

    private static final String SLACK_INCOMING_WEB_HOOK_URL = System.getenv("SLACK_INCOMING_WEB_HOOK_URL");
    private static final String SLACK_BOT_NAME =  "IP-Translator";
    private static final String SLACK_BOT_ICON_URL =  System.getenv("SLACK_BOT_ICON_URL");

    private final SlackClient slack;
    SlackMessageTextBuilder slackMessageTextBuilder;

    public SlackMessageSender() {
        slack = new SlackClient(SLACK_INCOMING_WEB_HOOK_URL);
        slack.setIconUrl(SLACK_BOT_ICON_URL);
        slack.setUsername(SLACK_BOT_NAME);
        slackMessageTextBuilder = new SlackMessageTextBuilder();
    }

    public void createAndSendSlackMessage(String ip, List<Map<String,String>> ipMetadataTable) {

        if (ipMetadataTable.isEmpty()) {
            sendSlackMessage(String.format("*No recent actions found for IP: %s*", ip));
        }
        else {
            sendSlackMessage(String.format("*Top unique actions in the last 2 hours for IP: %s *", ip));
            ipMetadataTable.forEach(row -> {
                SlackMessageText messageText = new SlackMessageText();
                messageText.setIpAddress(ip);
                messageText.setPrincipalName(row.get("principalName"));
                messageText.setAction(row.get("action"));
                messageText.setSdbName(row.get("sdbName"));
                messageText.setClientVersion(row.get("clientVersion"));
                messageText.setOwner(row.get("owner"));
                messageText.setCount(row.get("count"));

                sendSlackMessageWithBlocks(messageText);
            });
        }
    }

    private void sendSlackMessage(String text) {

        Payload payload = Payload.builder()
                .username(SLACK_BOT_NAME)
                .iconEmoji(SLACK_BOT_ICON_URL)
                .text(text)
                .build();

        Slack slack = Slack.getInstance();
        try {
            WebhookResponse response = slack.send(SLACK_INCOMING_WEB_HOOK_URL, payload);
        } catch (IOException e) {
            throw new RuntimeException("I/O error while communicating with Slack", e);
        }
    }

    private void sendSlackMessageWithBlocks(SlackMessageText messageText) {

        List<LayoutBlock> blocks = slackMessageTextBuilder.generateMessageBlocks(messageText);

        Payload payload = Payload.builder()
                .username(SLACK_BOT_NAME)
                .iconEmoji(SLACK_BOT_ICON_URL)
                .text("")
                .blocks(blocks)
                .build();

        Slack slack = Slack.getInstance();
        try {
            WebhookResponse response = slack.send(SLACK_INCOMING_WEB_HOOK_URL, payload);
        } catch (IOException e) {
            throw new RuntimeException("I/O error while communicating with Slack", e);
        }
    }
}
