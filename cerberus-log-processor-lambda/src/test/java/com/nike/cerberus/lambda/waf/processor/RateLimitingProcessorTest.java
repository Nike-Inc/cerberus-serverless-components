package com.nike.cerberus.lambda.waf.processor;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.waf.AWSWAFRegional;
import com.amazonaws.services.waf.model.ChangeAction;
import com.amazonaws.services.waf.model.GetChangeTokenRequest;
import com.amazonaws.services.waf.model.GetChangeTokenResult;
import com.amazonaws.services.waf.model.GetIPSetRequest;
import com.amazonaws.services.waf.model.GetIPSetResult;
import com.amazonaws.services.waf.model.IPSet;
import com.amazonaws.services.waf.model.IPSetDescriptor;
import com.amazonaws.services.waf.model.IPSetDescriptorType;
import com.amazonaws.services.waf.model.IPSetUpdate;
import com.amazonaws.services.waf.model.UpdateIPSetRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.nike.cerberus.lambda.waf.ALBAccessLogEvent;
import com.nike.cerberus.lambda.waf.LogProcessorLambdaConfig;
import com.nike.cerberus.lambda.waf.ViolationMetaData;
import org.apache.commons.net.util.SubnetUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class RateLimitingProcessorTest {

    public static final String CHANGE_TOKEN = "DOES NOT MATTER";
    RateLimitingProcessor processor;
    LogProcessorLambdaConfig config;
    private static final String FAKE_BUCKET_NAME = "blah";

    @Mock
    AWSWAFRegional awswaf;

    @Mock
    AmazonS3 amazonS3;

    @Mock
    LogProcessorLambdaConfig logProcessorLambdaConfig;

    @Before
    public void before() {
        initMocks(this);

        processor = spy(new RateLimitingProcessor(new ObjectMapper(), awswaf, amazonS3));

        GetChangeTokenResult token = mock(GetChangeTokenResult.class);
        when(awswaf.getChangeToken(isA(GetChangeTokenRequest.class))).thenReturn(token);
        when(token.getChangeToken()).thenReturn(CHANGE_TOKEN);

        config = logProcessorLambdaConfig;
    }

    @Test
    public void testThatFilterAndTruncateViolatorsRemoveExpiredBlocks() {
        when(config.getBlacklistDurationInMinutes()).thenReturn(1);
        RangeSet<Integer> rangeSet = TreeRangeSet.create();

        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("50.39.100.194", new ViolationMetaData(new Date(new Date().getTime() - 120000), 2));
        Map<String, ViolationMetaData> actual = processor.filterAndTruncateViolators(config, rangeSet, violators);

        assertTrue("The violators map should be empty after filtering", actual.isEmpty());
    }

    @Test
    public void testThatFilterAndTruncateViolatorsDoesNotRemoveNonExpiredBlocks() {
        when(config.getBlacklistDurationInMinutes()).thenReturn(1);
        RangeSet<Integer> rangeSet = TreeRangeSet.create();

        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("50.39.100.193", new ViolationMetaData(new Date(), 2));
        Map<String, ViolationMetaData> actual = processor.filterAndTruncateViolators(config, rangeSet, violators);

        assertTrue("The violators map should still have one entry after filtering", actual.size() == 1);
    }

    @Test
    public void testThatFilterAndTruncateViolatorsFiltersIPsInDoNotBlockRangeSet() {
        when(config.getBlacklistDurationInMinutes()).thenReturn(1);
        RangeSet<Integer> doNotAutoBlockIpRangeSet = TreeRangeSet.create();

        SubnetUtils subnetUtils = new SubnetUtils("50.39.100.193/32");
        subnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
        Integer lowIpAsInt = subnetInfo.asInteger(subnetInfo.getLowAddress());
        Integer highIpAsInt = subnetInfo.asInteger(subnetInfo.getHighAddress());
        doNotAutoBlockIpRangeSet.add(Range.closed(lowIpAsInt, highIpAsInt));

        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("50.39.100.193", new ViolationMetaData(new Date(), 2));
        Map<String, ViolationMetaData> actual = processor.filterAndTruncateViolators(config, doNotAutoBlockIpRangeSet,
                violators);

        assertTrue("The violators map should be empty after filtering", actual.size() == 0);
    }

    @Test
    public void testThatFilterAndTruncateViolatorsTruncatesTheLowestOffenders() {
        final int cidrLimit = 2;
        processor.setCidrLimitForIpSetOverride(cidrLimit);
        when(config.getBlacklistDurationInMinutes()).thenReturn(10);

        RangeSet<Integer> rangeSet = TreeRangeSet.create();

        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("50.39.100.193", new ViolationMetaData(new Date(), 3));
        violators.put("50.39.100.191", new ViolationMetaData(new Date(), 1));
        violators.put("50.39.100.192", new ViolationMetaData(new Date(), 2));
        violators.put("50.39.100.194", new ViolationMetaData(new Date(), 4));
        Map<String, ViolationMetaData> actual = processor.filterAndTruncateViolators(config, rangeSet, violators);

        assertTrue("The violators map should be the size of the cidr limit", actual.size() == cidrLimit);
        assertTrue("violators should contain 193 and 194 the highest offenders",
                actual.containsKey("50.39.100.193") && actual.containsKey("50.39.100.194"));
    }

    @Test
    public void testThatProcessViolatorsRemovesIpsThatShouldNoLongerBeInIpSet() {
        String fakeIpSet = "foo";
        when(config.getRateLimitAutoBlacklistIpSetId()).thenReturn(fakeIpSet);

        List<SubnetUtils.SubnetInfo> currentlyAutoBlocked = new LinkedList<>();
        SubnetUtils subnetUtils = new SubnetUtils("192.168.0.1/32");
        subnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo info = subnetUtils.getInfo();
        currentlyAutoBlocked.add(info);

        doReturn(currentlyAutoBlocked).when(processor).getIpSet(fakeIpSet, 0);

        Map<String, ViolationMetaData> violators = new HashMap<>();

        processor.processViolators(config, violators);

        List<IPSetUpdate> expectedUpdates = new LinkedList<>();
        IPSetDescriptor descriptor = new IPSetDescriptor()
                .withType(IPSetDescriptorType.IPV4)
                .withValue(info.getCidrSignature());

        IPSetUpdate update = new IPSetUpdate().withIPSetDescriptor(descriptor).withAction(ChangeAction.DELETE);
        expectedUpdates.add(update);

        verify(awswaf, times(1)).updateIPSet(new UpdateIPSetRequest()
                .withIPSetId(config.getRateLimitAutoBlacklistIpSetId())
                .withUpdates(expectedUpdates)
                .withChangeToken(CHANGE_TOKEN));
    }

    @Test
    public void testThatProcessViolatorsDoesNotReAddWhatIsAlreadyBlocked() {
        String fakeIpSet = "foo";
        when(config.getRateLimitAutoBlacklistIpSetId()).thenReturn(fakeIpSet);

        List<SubnetUtils.SubnetInfo> currentlyAutoBlocked = new LinkedList<>();
        SubnetUtils subnetUtils = new SubnetUtils("192.168.0.1/32");
        subnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo info = subnetUtils.getInfo();
        currentlyAutoBlocked.add(info);

        doReturn(currentlyAutoBlocked).when(processor).getIpSet(fakeIpSet, 0);

        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("192.168.0.1", new ViolationMetaData(new Date(), 10));

        processor.processViolators(config, violators);

        verify(awswaf, times(0)).updateIPSet(isA(UpdateIPSetRequest.class));
    }

    @Test
    public void testThatProcessViolatorsAddsNewIpsToIpSet() {
        String fakeIpSet = "foo";
        when(config.getRateLimitAutoBlacklistIpSetId()).thenReturn(fakeIpSet);
        doReturn(new LinkedList<>()).when(processor).getIpSet(fakeIpSet, 0);
        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("192.168.0.1", new ViolationMetaData(new Date(), 10));

        processor.processViolators(config, violators);

        List<IPSetUpdate> expectedUpdates = new LinkedList<>();
        SubnetUtils subnetUtils = new SubnetUtils("192.168.0.1/32");
        subnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo info = subnetUtils.getInfo();
        IPSetDescriptor descriptor = new IPSetDescriptor()
                .withType(IPSetDescriptorType.IPV4)
                .withValue(info.getCidrSignature());

        IPSetUpdate update = new IPSetUpdate().withIPSetDescriptor(descriptor).withAction(ChangeAction.INSERT);
        expectedUpdates.add(update);

        verify(awswaf, times(1)).updateIPSet(new UpdateIPSetRequest()
                .withIPSetId(config.getRateLimitAutoBlacklistIpSetId())
                .withUpdates(expectedUpdates)
                .withChangeToken(CHANGE_TOKEN));
    }

    @Test
    public void testThatGetCurrentlyBlockedIpsAndDateViolatedMapReturnsEmptyMapWhenS3HasNoObject() {
        when(amazonS3.getObject(isA(GetObjectRequest.class))).thenReturn(null);
        Map<String, ViolationMetaData> map = processor.getCurrentlyBlockedIpsAndDateViolatedMap(FAKE_BUCKET_NAME);
        assertTrue("The map should be empty", map.size() == 0);
    }

    @Test
    public void testThatGetCurrentlyBlockedIpsAndDateViolatedMapCanProperlyDeserialize() {
        String json = "{\"192.168.0.1\":{\"date\":1476224112155,\"maxRate\":10}}";
        S3Object object = new S3Object();
        object.setObjectContent(new ByteArrayInputStream(json.getBytes()));
        when(amazonS3.getObject(isA(GetObjectRequest.class))).thenReturn(object);
        Map<String, ViolationMetaData> map = processor.getCurrentlyBlockedIpsAndDateViolatedMap(FAKE_BUCKET_NAME);
        assertTrue("The map should have one element", map.size() == 1);
    }

    @Test
    public void testThatGetIpSetReturnsAListOfSubNetInfoObjects() {
        GetIPSetResult result = mock(GetIPSetResult.class);
        IPSet ipSet = mock(IPSet.class);
        when(result.getIPSet()).thenReturn(ipSet);

        List<IPSetDescriptor> descriptors = new LinkedList<>();
        descriptors.add(new IPSetDescriptor().withType(IPSetDescriptorType.IPV4).withValue("192.168.0.1/32"));
        descriptors.add(new IPSetDescriptor().withType(IPSetDescriptorType.IPV4).withValue("192.168.0.2/32"));
        descriptors.add(new IPSetDescriptor().withType(IPSetDescriptorType.IPV4).withValue("192.168.0.3/32"));

        when(ipSet.getIPSetDescriptors()).thenReturn(descriptors);
        when(awswaf.getIPSet(isA(GetIPSetRequest.class))).thenReturn(result);

        List<SubnetUtils.SubnetInfo> list = processor.getIpSet("DOES NOT MATTER", 0);
        assertTrue("The list should contain 3 items", list.size() == 3);
    }

    @Test
    public void testThatSaveCurrentViolatorsCallsPutObject() {
        Map<String, ViolationMetaData> violators = new HashMap<>();
        violators.put("192.168.0.1", new ViolationMetaData(new Date(), 10));

        processor.saveCurrentViolators(violators, FAKE_BUCKET_NAME);

        verify(amazonS3, times(1)).putObject(isA(PutObjectRequest.class));
    }

    @Test
    public void testThatGetDoNotBlockRangeSetBuildsARangeSet() {
        // stub the black list
        String black = "black";
        when(config.getManualBlacklistIpSetId()).thenReturn(black);
        List<SubnetUtils.SubnetInfo> blackList = Lists.newLinkedList();
        // 192.168.0.0-192.168.0.255
        SubnetUtils bSubnetUtils = new SubnetUtils("192.168.0.0/24");
        bSubnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo bInfo = bSubnetUtils.getInfo();
        blackList.add(bInfo);
        doReturn(blackList).when(processor).getIpSet(black, 0);

        // stub the white list
        String white = "white";
        when(config.getManualWhitelistIpSetId()).thenReturn(white);
        List<SubnetUtils.SubnetInfo> whiteList = Lists.newLinkedList();
        // 192.150.0.0-192.150.0.255
        SubnetUtils wSubnetUtils = new SubnetUtils("192.150.0.0/24");
        wSubnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo wInfo = wSubnetUtils.getInfo();
        whiteList.add(wInfo);
        doReturn(whiteList).when(processor).getIpSet(white, 0);

        RangeSet<Integer> doNotBlock = processor.getDoNotBlockRangeSet(config);

        SubnetUtils utils = new SubnetUtils("0.0.0.0/24");
        Integer onTheBlackListLow = utils.getInfo().asInteger("192.168.0.0");
        Integer onTheBlackListHigh = utils.getInfo().asInteger("192.168.0.255");
        Integer onTheWhiteListLow = utils.getInfo().asInteger("192.150.0.0");
        Integer onTheWhiteListHigh = utils.getInfo().asInteger("192.150.0.255");
        Integer shouldBeBlocked = utils.getInfo().asInteger("192.168.1.1");

        assertTrue("192.168.0.0 is in the ip range represented by 192.168.0.0/24 and therefor should be in the doNotBlock range set", doNotBlock.contains(onTheBlackListLow));
        assertTrue("192.168.0.255 is in the ip range represented by 192.168.0.0/24 and therefor should be in the doNotBlock range set", doNotBlock.contains(onTheBlackListHigh));
        assertTrue("192.150.0.0 is in the ip range represented by 192.150.0.0/24 and therefor should be in the doNotBlock range set", doNotBlock.contains(onTheWhiteListLow));
        assertTrue("192.150.0.255 is in the ip range represented by 192.150.0.0/24 and therefor should be in the doNotBlock range set", doNotBlock.contains(onTheWhiteListHigh));
        assertFalse("192.168.1.1 is not in 192.168.0.0/24 or 192.150.0.0/24 and should not be in the doNotBlock range set", doNotBlock.contains(shouldBeBlocked));
    }

    @Test
    public void testThatGetCurrentViolatorsReturnsAMapOfIpAddressToMetaData() {
        when(config.getRequestPerHourLimit()).thenReturn(2);
        Map<String, Integer> map = Maps.newHashMap();
        map.put("108.171.135.164", 10);
        map.put("108.171.135.160", 1);

        Map<String, ViolationMetaData> violators = processor.getCurrentViolators(map, config);

        assertTrue("The map should have one violator", violators.size() == 1);
        assertTrue("The map should should contain 108.171.135.164", violators.containsKey("108.171.135.164"));
        assertTrue(violators.get("108.171.135.164").getMaxRate() == 10);
    }

    @Test
    public void testThatGetCurrentViolatorsReturnsAMapOfIpAddressToMetaDataAndContainsTheHighestRate() {
        when(config.getRequestPerHourLimit()).thenReturn(2);
        Map<String, Integer> map = Maps.newHashMap();
        map.put("109.171.135.160", 10);
        map.put("109.171.135.160", 20);

        Map<String, ViolationMetaData> violators = processor.getCurrentViolators(map, config);

        assertTrue("The map should have one violator", violators.size() == 1);
        assertTrue("The map should should contain 109.171.135.164", violators.containsKey("109.171.135.160"));
        assertTrue(violators.get("109.171.135.160").getMaxRate() == 20);
    }
}
