package com.nike.cerberus

import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.model.PutRecordRequest
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.event.S3EventNotification
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.S3Object
import groovy.json.JsonSlurper
import org.apache.log4j.Logger

import java.nio.ByteBuffer

class Handler {

    def log = Logger.getLogger(getClass())
    AmazonS3Client s3
    AmazonKinesisClient kinesis

    Handler() {
        s3 = new AmazonS3Client()
        kinesis = new AmazonKinesisClient()
    }

    def handleBackupMetadata(S3Event s3Event) {
        def artemisStreamName = System.getenv('ARTEMIS_STREAM_NAME')
        def cerberusKey = System.getenv('CERBERUS_KEY')

        s3Event.records.each { S3EventNotification.S3EventNotificationRecord record ->
            String bucketName = record.s3.bucket
            String key = record.s3.object.key

            if (! key.endsWith("metadata.json")) {
                log.warn("Lambda triggered for ${bucketName}${key} skipping")
                return
            }

            log.info("Processing backup metadata from ${bucketName}${key}")
            S3Object metadataObject =  s3.getObject(new GetObjectRequest(bucketName, key))

            Map metadata = new JsonSlurper().parse(metadataObject.getObjectContent()) as Map

            // remove non metrics from map
            String cerberusUrl = metadata.remove('cerberusUrl')
            Date backupDate = metadata.remove('backupDate') as Date
            String accountId = metadata.remove('accountId')
            String backupRegion = metadata.remove('regionString')
            String env = (cerberusUrl - 'http://').split(/\./)[0]

            metadata.each { metricKey, metricValue ->
                kinesis.putRecord(new PutRecordRequest()
                        .withStreamName(artemisStreamName)
                        .withData(ByteBuffer.wrap([
                            "metric": metricKey,
                            "type": "counter",
                            "value": metricValue,
                            "cerberusKey": cerberusKey,
                            "dimensions": [
                                    environment: env,
                                    cerberusUrl: cerberusUrl,
                                    backupDate: backupDate,
                                    accountId: accountId,
                                    backupRegion: backupRegion
                            ],
                            "timestampMS": backupDate.time
                        ].toString().bytes))
                )
            }
        }
    }
}
