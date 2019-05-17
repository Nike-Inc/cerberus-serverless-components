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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class SlackMessage {

    private String token;
    private String teamId;
    private String teamDomain;
    private String channelId;
    private String channelName;
    private String timestamp;
    private String userId;
    private String userName;
    private String text;
    private String triggerWord;

    public String getToken() {
        return token;
    }

    public SlackMessage setToken(String token) {
        this.token = token;
        return this;
    }

    public String getTeamId() {
        return teamId;
    }

    public SlackMessage setTeamId(String teamId) {
        this.teamId = teamId;
        return this;
    }

    public String getTeamDomain() {
        return teamDomain;
    }

    public SlackMessage setTeamDomain(String teamDomain) {
        this.teamDomain = teamDomain;
        return this;
    }

    public String getChannelId() {
        return channelId;
    }

    public SlackMessage setChannelId(String channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public SlackMessage setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public SlackMessage setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public SlackMessage setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getUserName() {
        return userName;
    }

    public SlackMessage setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getText() {
        try {
            return URLDecoder.decode(text, "utf-8");
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    public SlackMessage setText(String text) {
        this.text = text;
        return this;
    }

    public String getTriggerWord() {
        return triggerWord;
    }

    public SlackMessage setTriggerWord(String triggerWord) {
        this.triggerWord = triggerWord;
        return this;
    }
}
