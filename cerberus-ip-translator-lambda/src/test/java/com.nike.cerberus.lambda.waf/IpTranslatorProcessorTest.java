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

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

import static org.junit.Assert.*;

public class IpTranslatorProcessorTest {

  private IpTranslatorProcessor processor;
  private SlackMessage slackMessage;

  @Before
  public void before() throws UnsupportedEncodingException {
    processor = new IpTranslatorProcessor();
    String message = "ALB+Log+Event+Handler+-+Rate+Limiting+Processor+run+summary%0ARunning+Environment%3A+demo2%0AIP+addresses+removed+from+auto+block+list%3A%0AIP+addresses+added+to+auto+block+list%3A+52.71.59.82+%28%3Chttp%3A%2F%2Fec2-52-71-59-82.compute-1.amazonaws.com%7Cec2-52-71-59-82.compute-1.amazonaws.com%3E%29%2C%0AIP+addresses+already+on+auto+block+list%3A";
    slackMessage = new SlackMessage().setText(message);
  }

  @Test
  public void test_that_isMessageFromRateLimiter_boolean_as_expected_true() {
    assertTrue(processor.isMessageFromRateLimiter(slackMessage));
  }

  @Test
  public void test_that_isMessageFromRateLimiter_boolean_as_expected_false() {
    assertFalse(processor.isMessageFromRateLimiter(new SlackMessage().setText("This message is not from the rate limiter")));
  }

  @Test
  public void test_that_getEnvironmentFromSlackMessage_returns_the_env_as_expected() {
    String env = processor.getEnvironmentFromSlackMessage(slackMessage);
    assertEquals("demo2", env);
  }

  @Test
  public void test_that_getIpsFromSlackMessage_returns_ips_as_expected() {
    List<String> ips = processor.getIpsFromSlackMessage(slackMessage);
    assertEquals(ImmutableList.of("52.71.59.82"), ips);
  }

}
