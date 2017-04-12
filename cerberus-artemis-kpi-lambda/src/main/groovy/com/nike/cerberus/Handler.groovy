package com.nike.cerberus

import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.model.PutRecordRequest
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.s3.AmazonS3Client
import com.fieldju.commons.EnvUtils
import com.fieldju.commons.StringUtils
import groovy.json.JsonSlurper
import org.apache.log4j.Logger

import java.nio.ByteBuffer

class Handler {

    def log = Logger.getLogger(getClass())
    AmazonS3Client s3
    AmazonKinesisClient kinesis

    Handler() {
        s3 = new AmazonS3Client()
        kinesis = AmazonKinesisClient.builder().standard().withRegion(
                EnvUtils.getRequiredEnv('ARTEMIS_STREAM_REGION', 'The Artemis Kinesis Stream region')
        ).build() as AmazonKinesisClient
    }

    def handleSnsEvent(SNSEvent snsEvent) {
        def artemisStreamName = EnvUtils.getRequiredEnv('ARTEMIS_STREAM_NAME', 'The Artemis Kinesis Stream')
        def cerberusKey =  EnvUtils.getRequiredEnv('CERBERUS_KEY', 'The Cerberus key for looking up the Artemis API Key')

        snsEvent.records.each { record ->

            CerberusMetricMessage msg = validateAndRetrieveMsg(record.getSNS())
            if (msg == null) {
                return
            }

            kinesis.putRecord(new PutRecordRequest()
                .withStreamName(artemisStreamName)
                .withData(ByteBuffer.wrap([
                    "metric": msg.metricKey,
                    "type": msg.metricType,
                    "value": msg.metricValue,
                    "cerberusKey": cerberusKey,
                    "dimensions": msg.dimensions,
                    "timestampMS": new Date().time
                ].toString().bytes))
            )
        }
    }

    CerberusMetricMessage validateAndRetrieveMsg(SNSEvent.SNS sns) {
        def subject = sns?.subject
        if (! StringUtils.equals(subject, 'cerberus-metric')) {
            log.info("Subject: ${subject} was not 'cerberus-metric' returning")
            return null
        }
        def serializedMsg = sns?.message
        if (StringUtils.isBlank(serializedMsg)) {
            log.error("The message: ${serializedMsg} was malformed")
            return null
        }

        log.info("Received msg with subject: ${subject} and body: ${serializedMsg}")

        def deserializedMsg = new JsonSlurper().parseText(serializedMsg)
        if (! deserializedMsg instanceof CerberusMetricMessage) {
            log.error("The message: ${serializedMsg} was malformed")
            return null
        }
        return deserializedMsg as CerberusMetricMessage
    }

    class CerberusMetricMessage {
        String metricKey, metricValue, metricType
        Map<String, String> dimensions = [:]
    }
}
