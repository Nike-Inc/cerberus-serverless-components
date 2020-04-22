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

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.athena.AmazonAthena;
import com.amazonaws.services.athena.AmazonAthenaClient;
import com.amazonaws.services.athena.model.*;
import org.apache.log4j.Logger;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static java.time.temporal.ChronoUnit.HOURS;

public class AthenaQuery {

    private final Logger logger = Logger.getLogger(getClass());

    /**
     * Creates Athena query to look up IP address and sends to Athena
     */
    public ResultSet processIpAddressInAthena(String ip, String env) {

        OffsetDateTime now = Instant.now().atOffset(ZoneOffset.UTC);
        OffsetDateTime back = now.minus(2, HOURS);
        logger.info("IP: " + ip);
        try {
            return executeAthenaQuery(
                "SELECT principal_name, action, sdb_name_slug, client_version, count(path) AS count " +
                    "FROM " + env + "_audit_db.audit_data " +
                    "WHERE ip_address = '" + ip + "' AND " + String.format("year >= %s AND month >= %s AND day >= %s AND hour >= %s ",
                    back.getYear(), back.getMonth().getValue(), back.getDayOfMonth(), back.getHour()) +
                    "GROUP BY principal_name, action, sdb_name_slug, client_version " +
                    "ORDER BY count DESC " +
                    "limit 10").getResultSet();
        } catch (Exception e) {
            throw new RuntimeException("Error running Athena query", e);
        }
    }

    /**
     * Executes an Athena query and waits for it to finish returning the results
     */
    private GetQueryResultsResult executeAthenaQuery(String query) throws InterruptedException {

        logger.info("QUERY: " + query);

        AmazonAthena athena = AmazonAthenaClient.builder()
            .withRegion(Regions.US_WEST_2)
            .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
            .build();

        StartQueryExecutionResult result = athena
            .startQueryExecution(new StartQueryExecutionRequest()
                .withQueryString(query)
                .withResultConfiguration(new ResultConfiguration().withOutputLocation("s3://aws-athena-query-results-933764306573-us-west-2/ip-address-translator"))
            );

        String id = result.getQueryExecutionId();

        String state;
        do {
            state = athena.getQueryExecution(new GetQueryExecutionRequest().withQueryExecutionId(id)).getQueryExecution().getStatus().getState();
            logger.info(String.format("Polling for query to finish: current status: %s", state));
            Thread.sleep(1000);
        } while (state.equals("RUNNING") || state.equals("QUEUED"));

        logger.info(String.format("The query: %s is in state: %s, fetching results", id, state));

        return athena.getQueryResults(new GetQueryResultsRequest().withQueryExecutionId(id));
    }
}
