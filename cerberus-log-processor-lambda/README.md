# Cerberus Log Processor Lambda

This is a JVM based lambda for processing access log events. It is part of the [edge security](http://engineering.nike.com/cerberus/docs/architecture/infrastructure-overview)
solution for [Cerberus](http://engineering.nike.com/cerberus/).

ALBAccessLogEventHandler::handleNewS3Event(), gets triggered every time the ALB saves a log to S3.
ALBAccessLogEventHandler has a list of processors that can ingest the events and do various things like rate limiting.

To learn more about Cerberus, please see the [Cerberus website](http://engineering.nike.com/cerberus/).

## Processors

### Rate Limiting Processor
This processor will go through access log events and ensures that ips that show up more than the requests per minute limit are added to the auto block list for the Cerberus Env WAF

### Google Analytics KPI Processor
If you optionally supply a Google Analytics Tracking ID, this processor will track API events such as auth events, sdb creation, etc.

### Future Processors
We would like to have a processor for auto blocking ips that spam bad requests.

## Building

To build the fat jar required for Lambda run `./gradlew cerberus-log-processor-lambda:sJ cerberus-log-processor-lambda:deploySam -Penv=[ENVIRONMENT]`
