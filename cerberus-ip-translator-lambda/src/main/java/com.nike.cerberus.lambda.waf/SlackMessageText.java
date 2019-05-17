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

public class SlackMessageText {

    private String ipAddress;
    private String principalName;
    private String action;
    private String sdbName;
    private String clientVersion;
    private String owner;
    private String count;

    public String getIpAddress() {
        return ipAddress;
    }

    public SlackMessageText setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public SlackMessageText setPrincipalName(String principalName) {
        this.principalName = principalName;
        return this;
    }

    public String getAction() {
        return action;
    }

    public SlackMessageText setAction(String action) {
        this.action = action;
        return this;
    }

    public String getSdbName() {
        return sdbName;
    }

    public SlackMessageText setSdbName(String sdbName) {
        this.sdbName = sdbName;
        return this;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public SlackMessageText setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
        return this;
    }

    public String getOwner() {
        return owner;
    }

    public SlackMessageText setOwner(String owner) {
        this.owner = owner;
        return this;
    }

    public String getCount() {
        return count;
    }

    public SlackMessageText setCount(String count) {
        this.count = count;
        return this;
    }
}
