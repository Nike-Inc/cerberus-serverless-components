package com.nike.cerberus

import com.fieldju.commons.EnvUtils
import com.nike.cerberus.client.CerberusClient
import com.nike.cerberus.client.DefaultCerberusClientFactory
import com.nike.cerberus.client.model.CerberusResponse
import com.nike.cerberus.model.ApiGatewayProxyResponse
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.log4j.Logger
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate

import static org.junit.Assert.*

/**
 * Entry point for the health check Lambda
 */
class HealthCheckHandler {

    private static final int FETCH_AND_VALIDATE_RETRY_LIMIT = 10
    private static final long FETCH_AND_VALIDATE_RETRY_SLEEP_IN_MILLI_SECONDS = 250

    private static Logger log = Logger.getLogger(getClass())
    def runHealthCheck() {
        String healthCheckPath = 'unknown'
        String healthCheckValueKey = 'unknown'
        String expectedHealthCheckValue = 'unknown'
        String cerberusEnvironment = 'unknown'
        String region = 'unknown'
        def authRetryCount = 'unknown'
        def fetchRetryCount = 'unknown'

        try {
            log.info 'Checking for required environmental Variables'
            String cerberusUrl = EnvUtils.getRequiredEnv('CERBERUS_URL')
            cerberusEnvironment = EnvUtils.getRequiredEnv('ENVIRONMENT')
            region = EnvUtils.getRequiredEnv('REGION')
            healthCheckPath = EnvUtils.getEnvWithDefault('HEALTH_CHECK_VALUE_PATH', 'app/health-check-bucket/healthcheck')
            healthCheckValueKey = EnvUtils.getEnvWithDefault('HEALTH_CHECK_VALUE_KEY', 'value')
            expectedHealthCheckValue = EnvUtils.getEnvWithDefault('HEALTH_CHECK_VALUE', 'I am healthy')

            log.info 'Creating Cerberus Clients'
            CerberusClient cerberusClient = DefaultCerberusClientFactory.getClient(cerberusUrl, region)

            // Fetching and validating health check value
            log.info 'Fetching health check value from cerberus'
            fetchRetryCount = fetchAndValidateHealthCheckValue(cerberusClient, healthCheckPath, healthCheckValueKey, expectedHealthCheckValue)
            log.info("Successfully validated Cerberus Health")
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

    private int fetchAndValidateHealthCheckValue(CerberusClient client,
                                                  String healthCheckPath,
                                                  String healthCheckValueKey,
                                                  String expectedHealthCheckValue,
                                                  int retryCount = 0) {

        try {
            CerberusResponse response = client.read(healthCheckPath)


            String actualHealthCheckValue = response.getData().get(healthCheckValueKey)

            assertEquals("The actual value for key: ${healthCheckValueKey} in response: ${actualHealthCheckValue} " +
                    "was not the expected value: ${expectedHealthCheckValue}", expectedHealthCheckValue, actualHealthCheckValue)

            return retryCount
        } catch (Throwable t) {
            log.error("Failed to fetch and validate health check value, retryCount: ${retryCount}", t)
            if (retryCount < FETCH_AND_VALIDATE_RETRY_LIMIT) {
                sleep(FETCH_AND_VALIDATE_RETRY_SLEEP_IN_MILLI_SECONDS)
                fetchAndValidateHealthCheckValue(client, healthCheckPath,
                        healthCheckValueKey, expectedHealthCheckValue, retryCount + 1)
            }
            throw t
        }
    }

    final static ApiGatewayProxyResponse success(Map<String, Object> data) {
        return new ApiGatewayProxyResponse([
                headers: [
                        'Content-Type': 'text/html; charset=utf-8'
                ],
                statusCode: 200,
                body: getRenderedTemplate(data)
        ])
    }

    final static ApiGatewayProxyResponse error(Map<String, Object> data) {
        return new ApiGatewayProxyResponse([
                headers: [
                        'Content-Type': 'text/html; charset=utf-8'
                ],
                statusCode: 500,
                body: getRenderedTemplate(data)
        ])
    }

    private static String getRenderedTemplate(Map<String, Object> data) {
        try {
            JtwigTemplate template = JtwigTemplate.classpathTemplate('templates/health-check-template.twig')
            JtwigModel model = JtwigModel.newModel(data)
            return template.render(model)
        } catch (Throwable t) {
            log.error("Failed to render template", t)
            return "${data}"
        }
    }
}
