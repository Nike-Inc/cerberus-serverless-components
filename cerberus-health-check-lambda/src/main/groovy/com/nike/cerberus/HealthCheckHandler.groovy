package com.nike.cerberus

import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.kms.model.DecryptRequest
import com.fieldju.commons.EnvUtils
import com.fieldju.commons.StringUtils
import com.nike.cerberus.model.ApiGatewayProxyResponse
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.log4j.Logger
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import static org.junit.Assert.*

/**
 * Entry point for the health check Lambda
 */
class HealthCheckHandler {

    private static final int AUTH_RETRY_LIMIT = 10
    private static final long AUTH_RETRY_SLEEP_IN_MILLI_SECONDS = 250
    private static final int FETCH_AND_VALIDATE_RETRY_LIMIT = 10
    private static final long FETCH_AND_VALIDATE_RETRY_SLEEP_IN_MILLI_SECONDS = 250
    private static final int DEFAULT_HTTP_CLIENT_TIMEOUT = 10
    private static final TimeUnit DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT = TimeUnit.SECONDS

    private static Logger log = Logger.getLogger(getClass())

    def runHealthCheck() {
        String healthCheckPath = 'unknown'
        String healthCheckValueKey = 'unknown'
        String expectedHealthCheckValue = 'unknown'
        String cerberusEnvironment = 'unknown'
        String region = 'unknown'
        int authRetryCount = 0
        int fetchRetryCount = 0

        try {
            log.info 'Checking for required environmental Variables'
            String cerberusUrl = EnvUtils.getRequiredEnv('CERBERUS_URL')
            cerberusEnvironment = EnvUtils.getRequiredEnv('ENVIRONMENT')
            String accountId = EnvUtils.getRequiredEnv('ACCOUNT_ID')
            String roleName = EnvUtils.getRequiredEnv('ROLE_NAME')
            region = EnvUtils.getRequiredEnv('REGION')
            healthCheckPath = EnvUtils.getEnvWithDefault('HEALTH_CHECK_VALUE_PATH', 'app/health-check-bucket/healthcheck')
            healthCheckValueKey = EnvUtils.getEnvWithDefault('HEALTH_CHECK_VALUE_KEY', 'value')
            expectedHealthCheckValue = EnvUtils.getEnvWithDefault('HEALTH_CHECK_VALUE', 'I am healthy')

            log.info 'Creating AWS and Cerberus Clients'
            OkHttpClient client = new OkHttpClient.Builder()
                    .hostnameVerifier(new NoopHostnameVerifier())
                    .connectTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT, DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT)
                    .writeTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT, DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT)
                    .readTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT, DEFAULT_HTTP_CLIENT_TIMEOUT_UNIT)
                    .build()

            AWSKMS kmsClient = AWSKMSClient.builder().withRegion(region).build()

            // Authenticating
            log.info 'Authenticating with Cerberus'
            String authToken = authenticate(kmsClient, client, cerberusUrl, accountId, roleName, region, authRetryCount)

            // Fetching and validating health check value
            log.info 'Fetching health check value from cerberus'
            fetchAndValidateHealthCheckValue(client, authToken, cerberusUrl, healthCheckPath, healthCheckValueKey, expectedHealthCheckValue, fetchRetryCount)
            return success([
                    environment: cerberusEnvironment,
                    status: 'healthy',
                    healthCheckPath: healthCheckPath,
                    healthCheckValueKey: healthCheckValueKey,
                    expectedHealthCheckValue: expectedHealthCheckValue,
                    region: region,
                    authRetryCount: authRetryCount,
                    fetchRetryCount: fetchRetryCount
            ])
        } catch (Throwable t) {
            return error([
                    environment: cerberusEnvironment,
                    status: 'unhealthy',
                    healthCheckPath: healthCheckPath,
                    healthCheckValueKey: healthCheckValueKey,
                    expectedHealthCheckValue: expectedHealthCheckValue,
                    region: region,
                    authRetryCount: authRetryCount,
                    fetchRetryCount: fetchRetryCount,
                    error: true,
                    throwableMessage: ExceptionUtils.getMessage(t),
                    throwableMessageStacktrace: ExceptionUtils.getStackTrace(t)
            ])
        }
    }

    private String authenticate(AWSKMS kmsClient,
                                OkHttpClient client,
                                String cerberusUrl,
                                String accountId,
                                String roleName,
                                String region,
                                int retryCount) {

        try {
            MediaType JSON = MediaType.parse("application/json; charset=utf-8")

            RequestBody body = RequestBody.create(JSON,
                    new JsonBuilder([
                            account_id: accountId,
                            role_name: roleName,
                            region: region
                    ]).toString())

            Request request = new Request.Builder()
                    .url("${cerberusUrl}/v1/auth/iam-role")
                    .post(body)
                    .build()
            Response response = client.newCall(request).execute()
            def bodyString = response.body().string()

            assertEquals("Failed to get a 200 while authenticating, code: ${response.code()}, body: ${bodyString}", 200, response.code())

            def authPayload = new JsonSlurper().parseText(bodyString)

            String encryptedAuthData = authPayload.auth_data
            def authResp = kmsClient.decrypt(new DecryptRequest()
                    .withCiphertextBlob(ByteBuffer.wrap(encryptedAuthData.decodeBase64())))
            def respString = new String(authResp.getPlaintext().array())
            def authData = new JsonSlurper().parseText(respString)

            String authToken = authData?.client_token
            assertTrue('The auth token should not be blank', StringUtils.isNotBlank(authToken))
            return authToken
        } catch (Throwable t) {
            log.error("Failed to authenticate with Cerberus, retryCount: ${retryCount}", t)
            if (retryCount < AUTH_RETRY_LIMIT) {
                sleep(AUTH_RETRY_SLEEP_IN_MILLI_SECONDS)
                return authenticate(kmsClient, client, cerberusUrl, accountId, roleName, region, retryCount + 1)
            }
            throw t
        }
    }

    private void fetchAndValidateHealthCheckValue(OkHttpClient client,
                                                  String authToken,
                                                  String cerberusUrl,
                                                  String healthCheckPath,
                                                  String healthCheckValueKey,
                                                  String expectedHealthCheckValue,
                                                  int retryCount) {

        try {
            Request request = new Request.Builder()
                    .url("${cerberusUrl}/v1/secret/${healthCheckPath}")
                    .addHeader('X-Vault-Token', authToken)
                    .get()
                    .build()

            Response response = client.newCall(request).execute()
            def resp = new JsonSlurper().parseText(response.body().string())
            String actualHealthCheckValue = resp?.data?."${healthCheckValueKey}"

            assertEquals("The actual value for key: ${healthCheckValueKey} in response: ${new JsonBuilder(resp).toString()} " +
                    "was not the expected value: ${expectedHealthCheckValue}", expectedHealthCheckValue, actualHealthCheckValue)

        } catch (Throwable t) {
            log.error("Failed to fetch and validate health check value, retryCount: ${retryCount}", t)
            if (retryCount < FETCH_AND_VALIDATE_RETRY_LIMIT) {
                sleep(FETCH_AND_VALIDATE_RETRY_SLEEP_IN_MILLI_SECONDS)
                fetchAndValidateHealthCheckValue(client, authToken, cerberusUrl, healthCheckPath,
                        healthCheckValueKey, expectedHealthCheckValue, retryCount + 1)
            }
            throw t
        }
    }

    final ApiGatewayProxyResponse success(Map<String, Object> data) {
        return new ApiGatewayProxyResponse([
                headers: [
                        'Content-Type': 'text/html; charset=utf-8'
                ],
                statusCode: 200,
                body: getRenderedTemplate(data)
        ])
    }

    final ApiGatewayProxyResponse error(Map<String, Object> data) {
        return new ApiGatewayProxyResponse([
                headers: [
                        'Content-Type': 'text/html; charset=utf-8'
                ],
                statusCode: 500,
                body: getRenderedTemplate(data)
        ])
    }

    private String getRenderedTemplate(Map<String, Object> data) {
        try {
            JtwigTemplate template = JtwigTemplate.classpathTemplate('templates/health-check-template.twig')
            JtwigModel model = JtwigModel.newModel(data)
            return template.render(model)
        } catch (Throwable t) {
            return "${data}"
        }
    }
}
