# Cerberus Clean Up API
This module contains CloudFormation that creates an API Gateway endpoint that clean up.

Once deployed you can perform a PUT on the `CleanUpEndpoint` endpoint.

A 204 (NO CONTENT) status code will return if the clean up call was successful.

Other non-200 codes can be returned from AWS itself.

## Deployment

This component is configured with the aws-sam-deployer-plugin

1. [Configure profiles](https://github.com/Nike-Inc/cerberus-serverless-components/blob/master/README.md#profiles)
2. Run the following gradle command `./gradlew cerberus-clean-up-lambda:sJ cerberus-clean-up-lambda:deploySam -Penv=[ENVIRONMENT]`

The plugin is configured to output the following


| Output                  | Description                                              |
| ----------------------- | -------------------------------------------------------- |
| `CleanUpEndpoint`       | The endpoint that you can PUT                            |

You can now use `CleanUpEndpoint` to clean up inactive and orphaned KMS key and IAM role data.