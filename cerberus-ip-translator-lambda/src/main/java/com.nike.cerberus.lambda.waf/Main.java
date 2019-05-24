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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

// User Acceptance Test for Handler
public class Main {

    public static void main(String [] args) {
        Map<String, Object> data = ImmutableMap.of(
                "body",

                "token=23DEpXRZsPczPOpXqCrm0YAC" +
                        "team_id=T3JSFAGH5&" +
                        "team_domain=awesom-o&" +
                        "service_id=119598257489&" +
                        "channel_id=C3JSFAKHD&" +
                        "channel_name=general&" +
                        "timestamp=1482533609.000478&" +
                        "user_id=U3JS3RQNA&" +
                        "user_name=fieldju&" +
                        "text=ALB Log Event Handler - Rate Limiting Processor run summary\\\n" +
                        "Running Environment: prod2\\\n" +
                        "IP addresses removed from auto block list: \\\n" +
                        "IP addresses added to auto block list: 34.249.129.124 (test.com), 34.251.252.31 (foo.bar.com), \\\n" +
                        "IP addresses already on auto block list:"
        );

        Handler handler = new Handler();
        handler.handleSlackOutgoingWebHookEvent(data);
    }

}
