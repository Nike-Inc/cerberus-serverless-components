import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.nike.cerberus.lambda.waf.handler.CloudFrontLogEventHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * An integration test that can be manipulated for UAT testing on the fly without needing to curl requests through the gateway
 */
public class CloudFrontLogEventHandlerIntegrationTest {

    private CloudFrontLogEventHandler handler;

    @Mock
    Context context;

    @Mock
    S3Event event;

    @Before
    public void before() {
        initMocks(this);
        handler = new CloudFrontLogEventHandler();

        String arn = System.getProperty("arn");
        String bucketName = System.getProperty("bucketName");
        String logKey = System.getProperty("logKey");

        Preconditions.checkNotNull(arn, "You must pass the arn for this lambda to run in a mocked manner, the arn is used to get the env name ex: arn:aws:lambda:us-west-2:1111111:function:dev-gateway-db2599d1-6d86-LambdaWAFBlacklistingFun-1LSORI5GUP95H");
        Preconditions.checkNotNull(bucketName, "You must supply a bucket for the lambda to read a log from");
        Preconditions.checkNotNull(logKey, "You must supply a key to a log for the lambda to read");

        List<S3EventNotification.S3EventNotificationRecord> records = Lists.newArrayList();
        S3EventNotification.S3EventNotificationRecord record = mock(S3EventNotification.S3EventNotificationRecord.class);
        records.add(record);

        when(event.getRecords()).thenReturn(records);

        S3EventNotification.S3Entity s3Entity = mock(S3EventNotification.S3Entity.class);
        S3EventNotification.S3BucketEntity bucketEntity = mock(S3EventNotification.S3BucketEntity.class);
        S3EventNotification.S3ObjectEntity objectEntity = mock(S3EventNotification.S3ObjectEntity.class);
        when(s3Entity.getBucket()).thenReturn(bucketEntity);
        when(s3Entity.getObject()).thenReturn(objectEntity);

        when(record.getS3()).thenReturn(s3Entity);

        when(context.getInvokedFunctionArn()).thenReturn(arn);
        when(bucketEntity.getName()).thenReturn(bucketName);
        when(objectEntity.getKey()).thenReturn(logKey);
    }

    @Test
    public void endToEndTest() throws IOException {
        // this test doesn't actually assert anything, but it allows me to trigger the lambda in a
        // controlled way and verify things manually, and attach a debugger to real running code
        handler.handleNewS3Event(event, context);
    }

}
