# Cerberus Artemis KPI Lambda

This is an example reference serverless function for processing Cerberus related metrics.
While you likely will not use this yourself you can see that we take messages from the Cerberus Metrics Topic 
and after transforming them submit them to a Kinesis stream for an internal Lambda to submit to a metrics system such
 as SignalFx or New Relic.