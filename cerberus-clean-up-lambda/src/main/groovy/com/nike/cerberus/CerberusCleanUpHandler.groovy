package com.nike.cerberus

import com.fieldju.commons.EnvUtils
import com.nike.cerberus.client.auth.aws.StaticIamRoleVaultCredentialsProvider
import groovy.json.JsonBuilder
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.log4j.Logger

import java.util.concurrent.TimeUnit

/**
 * Entry point for the clean up Lambda
 */
class CerberusCleanUpHandler {

    private static final int DEFAULT_HTTP_CLIENT_TIMEOUT = 10
    private static final TimeUnit DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT = TimeUnit.SECONDS

    private static Logger log = Logger.getLogger(getClass())

    def runCleanUp() {
        String cleanUpPath = '/v1/cleanup'
        String cerberusEnvironment = 'unknown'
        String region = 'unknown'

        try {
            log.info 'Checking for required environmental Variables'
            String cerberusUrl = EnvUtils.getRequiredEnv('CERBERUS_URL')
            cerberusEnvironment = EnvUtils.getRequiredEnv('ENVIRONMENT')
            String iamPrincipalArn = EnvUtils.getRequiredEnv('IAM_PRINCIPAL_ARN')
            region = EnvUtils.getRequiredEnv('REGION')

            log.info 'Authenticating with Cerberus'
            def credsProvider = new StaticIamRoleVaultCredentialsProvider(cerberusUrl, iamPrincipalArn, region)
            def authResult = credsProvider.getCredentials()
            def authToken = authResult.token

            log.info 'Creating Cerberus client'
            OkHttpClient client = new OkHttpClient.Builder()
                    .hostnameVerifier(new NoopHostnameVerifier())
                    .connectTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT, DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT)
                    .writeTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT, DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT)
                    .readTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT, DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT)
                    .build()

            def statusCode = cleanUpOrphanedAndInactiveRecords(client, authToken, cleanUpPath, cerberusUrl)
            if (statusCode != 204) {
                throw new IllegalStateException("Clean up was not successful!")
            }


            log.info("Successfully executed cleanup")
            return [
                    environment: cerberusEnvironment,
                    success: true,
                    cleanUpPath: cleanUpPath,
                    region: region,
            ]
        } catch (Throwable t) {
            return [
                    environment: cerberusEnvironment,
                    status: 'failed',
                    cleanUpPath: cleanUpPath,
                    region: region,
                    error: true,
                    throwableMessage: ExceptionUtils.getMessage(t),
                    throwableMessageStacktrace: ExceptionUtils.getStackTrace(t)
            ]
        }
    }

    private int cleanUpOrphanedAndInactiveRecords(OkHttpClient client,
                                                  String authToken,
                                                  String cleanupPath,
                                                  String cerberusUrl) {

        try {
            MediaType JSON = MediaType.parse("application/json; charset=utf-8")

            RequestBody body = RequestBody.create(JSON,
                    new JsonBuilder([
                            kms_expiration_period_in_days: 30
                    ]).toString())

            Request request = new Request.Builder()
                    .url("${cerberusUrl}${cleanupPath}")
                    .addHeader('X-Vault-Token', authToken)
                    .put(body)
                    .build()

            def response = client.newCall(request).execute()

            return response.code()
        } catch (Throwable t) {
            log.error("Failed to clean up orphaned and inactive records", t)
            throw t
        }
    }
}
