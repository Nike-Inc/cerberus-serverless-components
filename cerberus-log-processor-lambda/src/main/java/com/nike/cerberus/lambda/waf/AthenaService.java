package com.nike.cerberus.lambda.waf;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.athena.AmazonAthena;
import com.amazonaws.services.athena.AmazonAthenaClientBuilder;
import com.amazonaws.services.athena.model.GetQueryExecutionRequest;
import com.amazonaws.services.athena.model.GetQueryExecutionResult;
import com.amazonaws.services.athena.model.GetQueryResultsRequest;
import com.amazonaws.services.athena.model.GetQueryResultsResult;
import com.amazonaws.services.athena.model.QueryExecutionContext;
import com.amazonaws.services.athena.model.QueryExecutionState;
import com.amazonaws.services.athena.model.QueryExecutionStatus;
import com.amazonaws.services.athena.model.ResultConfiguration;
import com.amazonaws.services.athena.model.Row;
import com.amazonaws.services.athena.model.StartQueryExecutionRequest;
import com.amazonaws.services.athena.model.StartQueryExecutionResult;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AthenaService {
    private final Logger LOGGER = Logger.getLogger(getClass());
    private final AmazonAthena client;
    private final String databaseName;
    private final String tableName;
    private final String accountId;
    private final String logBucketName;
    private final String resultBucketName;
    private final Regions region;

    private final String PARTITION_QUERY_TEMPLATE = "ALTER TABLE %s ADD PARTITION (log_time='%s') LOCATION 's3://%s/AWSLogs/%s/elasticloadbalancing/%s%s';";
    private final String SELECT_QUERY_TEMPLATE = "SELECT * FROM %s WHERE log_time>='%s' AND time>='%s';";

    public AthenaService(String databaseName, String tableName, String accountId, String logBucketName, String resultBucketName, Regions region){
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.accountId = accountId;
        this.logBucketName = logBucketName;
        this.resultBucketName = resultBucketName;
        this.region = region;
        client = AmazonAthenaClientBuilder.standard()
                .withRegion(region)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();
    }

    public AthenaService(LogProcessorLambdaConfig config){
        this(config.getAthenaDatabaseName(),
                config.getAthenaTableName(),
                config.getIamPrincipalArn().split(":")[4],
                config.getAlbLogBucketName(),
                config.getAthenaQueryResultBucketName(),
                config.getRegion());
    }

    public List<List<String>> getLogEntrysAfter(DateTime time){

        // convert to UTC which is what AWS ALB is using
        time = time.withZone(DateTimeZone.UTC);
        String logtime = getLogtime(time);

        // partition new data before querying data
        DateTime now = DateTime.now(DateTimeZone.UTC);
        String nowPath = getPath(now);
        String nowLogtime = getLogtime(now);
        String partitionQuery = assembleAddPartitionQuery(nowPath, nowLogtime);
        try {
            String requestId = submitAthenaQuery(partitionQuery);
            waitForQueryToComplete(requestId);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() + " Error running Athena query " + partitionQuery, e);
        }
        List<List<String>> logEntries;
        String selectQuery = assembleSelectQuery(logtime, time.toString());
        try {
            String requestId = submitAthenaQuery(selectQuery);
            waitForQueryToComplete(requestId);
            logEntries = getResultRows(requestId);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() + " Error running Athena query " + selectQuery, e);
        }
        return logEntries;
    }

    private String assembleAddPartitionQuery(String path, String logtime) {
        return String.format(PARTITION_QUERY_TEMPLATE, tableName, logtime, logBucketName, accountId, region.getName(), path);
    }

    private String assembleSelectQuery(String logtime, String time) {
        return String.format(SELECT_QUERY_TEMPLATE, tableName, logtime, time);
    }

    /**
     * Submits a query to Athena and returns the execution ID of the query.
     */
    public String submitAthenaQuery(String query)
    {
        // The QueryExecutionContext allows us to set the Database.
        QueryExecutionContext queryExecutionContext = new QueryExecutionContext().withDatabase(databaseName);

        // The result configuration specifies where the results of the query should go in S3 and encryption options
        ResultConfiguration resultConfiguration = new ResultConfiguration()
                // You can provide encryption options for the output that is written.
                // .withEncryptionConfiguration(encryptionConfiguration)
                .withOutputLocation("s3://" + resultBucketName);

        // Create the StartQueryExecutionRequest to send to Athena which will start the query.
        StartQueryExecutionRequest startQueryExecutionRequest = new StartQueryExecutionRequest()
                .withQueryString(query)
                .withQueryExecutionContext(queryExecutionContext)
                .withResultConfiguration(resultConfiguration);

        StartQueryExecutionResult startQueryExecutionResult = client.startQueryExecution(startQueryExecutionRequest);
        LOGGER.debug("Athena query execution request sent for:" + query);
        return startQueryExecutionResult.getQueryExecutionId();
    }


    /**
     * Wait for an Athena query to complete, fail or to be cancelled. This is done by polling Athena over an
     * interval of time. If a query fails or is cancelled, then it will throw an exception.
     */
    public void waitForQueryToComplete(String queryExecutionId) throws InterruptedException
    {
        GetQueryExecutionRequest getQueryExecutionRequest = new GetQueryExecutionRequest()
                .withQueryExecutionId(queryExecutionId);

        GetQueryExecutionResult getQueryExecutionResult = null;
        boolean isQueryStillRunning = true;
        while (isQueryStillRunning) {
            getQueryExecutionResult = client.getQueryExecution(getQueryExecutionRequest);
            QueryExecutionStatus queryExecutionStatus = getQueryExecutionResult.getQueryExecution().getStatus();
            String queryState = queryExecutionStatus.getState();
            if (queryState.equals(QueryExecutionState.FAILED.toString())) {
                if (queryExecutionStatus.getStateChangeReason().contains("AlreadyExistsException")) {
                    LOGGER.info("Partition already exists.");
                    isQueryStillRunning = false;
                } else {
                    throw new RuntimeException("Query Failed to run with Error Message: " + getQueryExecutionResult.getQueryExecution().getStatus().getStateChangeReason());
                }

            }
            else if (queryState.equals(QueryExecutionState.CANCELLED.toString())) {
                throw new RuntimeException("Query was cancelled.");
            }
            else if (queryState.equals(QueryExecutionState.SUCCEEDED.toString())) {
                isQueryStillRunning = false;
            }
            else {
                // Sleep an amount of time before retrying again.
                Thread.sleep(1000);
            }
            LOGGER.debug("Current Status is: " + queryState);
        }
    }

    /**
     * This code calls Athena and retrieves the results of a query.
     * The query must be in a completed state before the results can be retrieved and
     * paginated. The first row of results are the column headers.
     */
    public List<List<String>> getResultRows(String queryExecutionId)
    {
        GetQueryResultsRequest getQueryResultsRequest = new GetQueryResultsRequest()
                // Max Results can be set but if its not set,
                // it will choose the maximum page size
                // As of the writing of this code, the maximum value is 1000
                // .withMaxResults(1000)
                .withQueryExecutionId(queryExecutionId);

        GetQueryResultsResult getQueryResultsResult = client.getQueryResults(getQueryResultsRequest);

        List<List<String>> rows = new ArrayList<>();
        while (true) {
            List<Row> results = getQueryResultsResult.getResultSet().getRows();
            for (Row row : results) {
                // Process the row. The first row of the first page holds the column names.
                rows.add(getRow(row));
            }
            // If nextToken is null, there are no more pages to read. Break out of the loop.
            if (getQueryResultsResult.getNextToken() == null) {
                break;
            }
            getQueryResultsResult = client.getQueryResults(
                    getQueryResultsRequest.withNextToken(getQueryResultsResult.getNextToken()));
        }
        // remove column names
        rows.remove(0);
        return rows;
    }


    private List<String> getRow(Row row) {
        return row.getData().stream().map(datum -> datum.getVarCharValue()).collect(Collectors.toList());
    }

    private String getPath(DateTime dateTime) {
        return String.format("/%04d/%02d/%02d", dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth());
    }

    private String getLogtime(DateTime dateTime) {
        return String.format("%04d-%02d-%02d", dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth());
    }
}
