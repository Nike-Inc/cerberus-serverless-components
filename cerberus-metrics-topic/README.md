# Cerberus Metrics Topic

This template creates an SNS topic that is used by various components to publish Cerberus metrics to, if enabled.
This topic must be created in each region that you want to track metrics in.

## todo more examples here

## Deploy

After configuring a global profile, you can simply run. 

    ./gradlew cerberus-metrics-topic:multiRegionDeploySam --stacktrace
 