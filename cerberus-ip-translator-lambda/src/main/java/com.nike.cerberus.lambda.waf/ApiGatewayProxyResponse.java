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

import java.util.Map;

public class ApiGatewayProxyResponse {

  private int statusCode;
  private Map<String, String> headers;
  private String body;

  public int getStatusCode() {
    return statusCode;
  }

  public ApiGatewayProxyResponse setStatusCode(int statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public ApiGatewayProxyResponse setHeaders(Map<String, String> headers) {
    this.headers = headers;
    return this;
  }

  public String getBody() {
    return body;
  }

  public ApiGatewayProxyResponse setBody(String body) {
    this.body = body;
    return this;
  }
}
