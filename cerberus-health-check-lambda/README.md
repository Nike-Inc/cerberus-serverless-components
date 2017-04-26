# Cerberus Health Check API
This module contains CloudFormation that creates an API Gateway endpoint that triggers a Lambda that authenticates and reads a secret from Cerberus.

Once deployed you can perform a GET on the `HealthCheckEndpoint` endpoint.
 
A 200 status code will be return when healthy.
A 500 status code will be returned when unhealthy.
Regardless of status a simple html page is returned that has the status, if unhealthy what went wrong will be on the page.

Other non-200 codes can be returned from AWS itself.

By default the serverless function will search for an SDB and node with path: `app/health-check-bucket/healthcheck`
and a key of `value` and a value of `I am healthy`. You can set the following env vars for the lambda to override these default values.

| Env Variable              | Default Value                       | Description                                        | 
| ------------------------- | ----------------------------------- | -------------------------------------------------- |
| `HEALTH_CHECK_VALUE_PATH` | app/health-check-bucket/healthcheck | The path to a secret node                          |
| `HEALTH_CHECK_VALUE_KEY`  | value                               | The key in the node data map to read a value from  |
| `HEALTH_CHECK_VALUE`      | I am healthy                        | The value of the key to make an equal assertion on |

## Deployment

This component is configured with the aws-sam-deployer-plugin

1. [Configure profiles](https://github.com/Nike-Inc/cerberus-serverless-components/blob/master/README.md#profiles)
2. Run the following gradle command `./gradlew cerberus-health-check-lambda:deploy --stacktrace`

The plugin is configured to output the following


| Output                  | Description                                              | 
| ----------------------- | -------------------------------------------------------- |
| `HealthCheckEndpoint`   | The endpoint that you can GET                            |
| `HealthCheckIamRoleArn` | The ARN to give read permissions in the Health Check SDB |

Make sure to set up an SDB in Cerberus that grants `HealthCheckIamRoleArn` read permission.

You can now use `HealthCheckEndpoint` in your monitoring solution.