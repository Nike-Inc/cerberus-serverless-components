# Cerberus Metrics Topic

This template creates an SNS topic that is used by various components to publish Cerberus metrics to, if enabled.
This topic must be created in each region that you want to track metrics in.

## Messages
Messages should conform to the following json schema, where dimensions is a k,v map

    {
        "$schema": "http://json-schema.org/draft-04/schema#",
        "additionalProperties": false,
        "definitions": {},
        "id": "http://example.com/example.json",
        "properties": {
            "dimensions": {
                "additionalProperties": { "type": "string" },
                "id": "/properties/dimensions",
                "properties": {},
                "type": "object"
            },
            "metricKey": {
                "id": "/properties/metricKey",
                "type": "string"
            },
            "metricType": {
                "id": "/properties/metricType",
                "type": "string"
            },
            "metricValue": {
                "id": "/properties/metricValue",
                "type": "string"
            }
        },
        "required": [
            "metricKey",
            "metricValue",
            "dimensions",
            "metricType"
        ],
        "type": "object"
    }

Example message

    {
      "metricKey": "iam-auth-event",
      "metricValue": "1",
      "metricType": "counter",
      "dimensions": {
        "iam-principal-arn": "arn:aws:iam::123456789012:role/microservice-foo",
        "environment": "dev"
      }
    }

## Deploy

After configuring a global profile, you can simply run. 

    ./gradlew cerberus-metrics-topic:multiRegionDeploySam --stacktrace
    
After deploying, you can get the topic arn as a CloudFormation export variable `CerberusMetricsTopicARN` in the regions you elected to deploy to.
 