# Cerberus CloudFront Lambda

[ ![Download](https://api.bintray.com/packages/nike/maven/cerberus-cloudfront-lambda/images/download.svg) ](https://bintray.com/nike/maven/cerberus-cloudfront-lambda/_latestVersion)
[ ![][https://api.travis-ci.org/Nike-Inc/cerberus-cloudfront-lambda.svg?branch=master] ][https://travis-ci.org/Nike-Inc/cerberus-cloudfront-lambda]
[ ![Code Coverage](https://img.shields.io/codecov/c/github/Nike-Inc/cerberus-cloudfront-lambda/master.svg) ](https://codecov.io/github/Nike-Inc/cerberus-cloudfront-lambda?branch=master)
[ ![][https://img.shields.io/badge/License-Apache%202-blue.svg] ][LICENSE.txt]

This is a Java based lambda for processing CloudFront log events.

CloudFrontLogEventHandler::handleNewS3Event(), gets triggered every time Cloud Front saves its logs to S3.
CloudFrontLogEventHandler has a list of processors that can ingest the events and do various things like rate limiting.

## Processors

### Rate Limiting Processor
This processor will go through the Cloud Front Log Events and ensures that ips that show up more than the requests per minute limit are added to the auto block list for the Cerberus Env WAF

### Future Processors
We would like to have a processor for auto blocking ips that spam bad requests and possibly another processor for KPI tracking.

## Building

To build the fat jar required for Lambda run `./gradlew clean shadowJar`

## License

Vault client is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)