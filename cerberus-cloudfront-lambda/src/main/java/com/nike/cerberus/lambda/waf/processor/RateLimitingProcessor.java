package com.nike.cerberus.lambda.waf.processor;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.waf.AWSWAF;
import com.amazonaws.services.waf.model.ChangeAction;
import com.amazonaws.services.waf.model.GetChangeTokenRequest;
import com.amazonaws.services.waf.model.GetChangeTokenResult;
import com.amazonaws.services.waf.model.GetIPSetRequest;
import com.amazonaws.services.waf.model.GetIPSetResult;
import com.amazonaws.services.waf.model.IPSetDescriptor;
import com.amazonaws.services.waf.model.IPSetDescriptorType;
import com.amazonaws.services.waf.model.IPSetUpdate;
import com.amazonaws.services.waf.model.UpdateIPSetRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldju.slackclient.Message;
import com.fieldju.slackclient.SlackClient;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import com.nike.cerberus.lambda.waf.CloudFrontLogEvent;
import com.nike.cerberus.lambda.waf.ViolationMetaData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This processor will process CF Log events and auto black list ips that violate the defined rate limit
 */
public class RateLimitingProcessor implements Processor {

    private final Logger log = Logger.getLogger(getClass());

    protected static final String SERIALIZED_DATA_FILE_NAME = "rate_limiting_lambda_ip_blocking_data.json";
    private static final int LIMIT_IP_ADDRESS_RANGES_PER_IP_MATCH_CONDITION = 1000;
    private static final String NETMASK_FOR_SINGLE_IP = "255.255.255.255";

    private final ObjectMapper objectMapper;
    private final AWSWAF awsWaf;
    private final AmazonS3 amazonS3;
    private int cidrLimitForIpSet = LIMIT_IP_ADDRESS_RANGES_PER_IP_MATCH_CONDITION;

    public RateLimitingProcessor(ObjectMapper objectMapper, AWSWAF awsWaf, AmazonS3 amazonS3) {
        this.objectMapper = objectMapper;
        this.awsWaf = awsWaf;
        this.amazonS3 = amazonS3;
    }

    public void setCidrLimitForIpSetOverride(int limitOverride) {
        cidrLimitForIpSet = limitOverride;
    }

    /**
     * 1. Build a map of requests identified by ip-date-time(minutes accuracy) this will give us rate / min by ip.
     * 2. Create a range set for ips we do not want to auto block that we can query.
     * 3. Collect the do not auto block range set, the ranges of ips for the manual block list.
     * 4. Get the current violators from the request map that have requests grouped by ip and minutes
     * 5. Filter and Truncate to remove expired IP address and IPs that are on the White or Black manual lists
     *      and ensure that we are under the ip limit for an IP Set
     * 6. Save the violators data.
     * 7. Update the auto block ip set to reflect the current violators data.
     *
     * @param events The Cloud Front log events
     * @param config The Cloud Formation outputs from when this Lambda was created
     * @param bucketName The Bucket that we are operating from
     */
    @Override
    public void processLogEvents(List<CloudFrontLogEvent> events, CloudFrontLogHandlerConfig config, String bucketName) {

        Map<String, Integer> reqIdCountMap = new HashMap<>();

        // Build a map of requests identified by ip-date-time(minutes accuracy) this will give us rate / min by ip
        events.forEach(cloudFrontLogEvent -> processRequest(cloudFrontLogEvent, reqIdCountMap));
        // Collect the do not auto block range set, the ranges of ips for the manual block and white list.
        RangeSet<Integer> doNotAutoBlockIpRangeSet = getDoNotBlockRangeSet(config);
        // Get the current violators
        Map<String, ViolationMetaData> violators = getCurrentViolators(reqIdCountMap, config);
        // Get and merge in all the currently blocked violators
        violators.putAll(getCurrentlyBlockedIpsAndDateViolatedMap(bucketName));
        // Filter and truncate to remove expired blocks and ensure that we are under the ip limit for an IP Set
        violators = filterAndTruncateViolators(config, doNotAutoBlockIpRangeSet, violators);
        // Save the violators data.
        saveCurrentViolators(violators, bucketName);
        // Update the auto block ip set to reflect the current violators data.
        Map<String, List<String>> summery = processViolators(config, violators);
        // log summery
        logSummery(summery, config);

    }

    private void logSummery(Map<String, List<String>> summery, CloudFrontLogHandlerConfig config) {
        List<String> ipsRemoved = summery.get("removed");
        List<String> ipsAdded = summery.get("added");
        List<String> ipsAlreadyBlocked = summery.get("duplicate");

        if (ipsRemoved.isEmpty() && ipsAdded.isEmpty() && ipsAlreadyBlocked.isEmpty()) {
            return;
        }

        StringBuilder builder = new StringBuilder("Cloud Front Log Event Handler - Rate Limiting Processor run summary\n");
        builder.append("Running Environment: ").append(config.getEnv()).append("\n");
        builder.append("IP addresses removed from auto block list: ");
        ipsRemoved.stream().sorted().forEach(ip -> builder.append(ip).append(", "));
        builder.append("\n");
        builder.append("IP addresses added to auto block list: ");
        ipsAdded.stream().sorted().forEach(ip -> builder.append(ip).append(", "));
        builder.append("\n");
        builder.append("IP addresses already on auto block list: ");
        ipsAlreadyBlocked.stream().sorted().forEach(ip -> builder.append(ip).append(", "));

        String text = builder.toString();
        if (StringUtils.isNotBlank(config.getSlackWebHookUrl())) {
            Message.Builder msgBuilder = new Message.Builder(text).userName("Rate-Limiting-Processor");
            if (StringUtils.startsWith(config.getSlackIcon(), "http")) {
                msgBuilder.iconUrl(config.getSlackIcon());
            } else {
                msgBuilder.iconEmoji(config.getSlackIcon());
            }
            new SlackClient(config.getSlackWebHookUrl()).sendMessage(msgBuilder.build());
        }
        log.info(text);
    }

    /**
     * Goes through the Manual white and black list to create a searchable range set of IP Address ranges from the
     * CIDRs of IPs to not add to the auto black list
     *
     * @param config The params from the Cloud Formation that created this Lambda.
     * @return The searchable range set of ips not to auto block.
     */
    protected RangeSet<Integer> getDoNotBlockRangeSet(CloudFrontLogHandlerConfig config) {
        // Create a range set for ips we do not want to auto block that we can query
        RangeSet<Integer> doNotAutoBlockIpRangeSet = TreeRangeSet.create();
        // Collect and add to the do not auto block range set, the ranges of ips for the manual block list.
        getIpSet(config.getManualBlackListIpSetId()).forEach(subnetInfo -> {
            Integer lowIpAsInt = subnetInfo.asInteger(subnetInfo.getLowAddress());
            Integer highIpAsInt = subnetInfo.asInteger(subnetInfo.getHighAddress());
            doNotAutoBlockIpRangeSet.add(Range.closed(lowIpAsInt, highIpAsInt));
        });
        // Collect and add to the do not auto block range set, the ranges of ips for the manual whitelist list.
        getIpSet(config.getManualWhiteListIpSetId()).forEach(subnetInfo -> {
            Integer lowIpAsInt = subnetInfo.asInteger(subnetInfo.getLowAddress());
            Integer highIpAsInt = subnetInfo.asInteger(subnetInfo.getHighAddress());
            doNotAutoBlockIpRangeSet.add(Range.closed(lowIpAsInt, highIpAsInt));
        });
        return doNotAutoBlockIpRangeSet;
    }

    /**
     * Truncate the list if greater than the WAF Limit, we will remove the violators with the lowest rate,
     * Filtering ips that are on the manually blocked list or white listed,
     * Also filtering out IPs that should no longer be blocked.
     */
    protected Map<String, ViolationMetaData> filterAndTruncateViolators(CloudFrontLogHandlerConfig params,
                                                                        RangeSet<Integer> doNotAutoBlockIpRangeSet,
                                                                        Map<String, ViolationMetaData> violators) {

        Date now = new Date();

        return violators.entrySet().stream()
                    .filter(entry -> TimeUnit.MILLISECONDS.toMinutes(now.getTime() - entry.getValue().getDate().getTime()) < params.getRateLimitViolationBlacklistPeriodInMinutes())
                    .filter(entry -> canAddToAutoBlackList(doNotAutoBlockIpRangeSet, entry.getKey()))
                    .sorted((o1, o2) -> o2.getValue().getMaxRate() - o1.getValue().getMaxRate())
                    .limit(cidrLimitForIpSet)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Given a map of violators this function goes through them and syncs the Auto Block IP Set to match it.
     * Removing IPs from the IP Set that are not in the map and adding the new IPs.
     *
     * @param config The params for this Lambda
     * @param violators The map of violators that need to be blocked
     */
    protected Map<String, List<String>> processViolators(CloudFrontLogHandlerConfig config, Map<String, ViolationMetaData> violators) {
        Map<String, List<String>> summery = Maps.newHashMap();
        summery.put("removed", new LinkedList<>());
        summery.put("added", new LinkedList<>());
        summery.put("duplicate", new LinkedList<>());

        Set<String> ipToBlock = violators.keySet();
        List<IPSetUpdate> updates = new LinkedList<>();

        // Remove ips from the auto blocked ip set that are not on our list, aka remove expired blocks
        getIpSet(config.getRateLimitAutoBlackListIpSetId()).forEach(subnetInfo -> {
            String ip = subnetInfo.getAddress();
            if (! ipToBlock.contains(ip)) {
                String cidr = new SubnetUtils(ip, NETMASK_FOR_SINGLE_IP).getInfo().getCidrSignature();
                IPSetDescriptor descriptor = new IPSetDescriptor().withType(IPSetDescriptorType.IPV4).withValue(cidr);
                IPSetUpdate update = new IPSetUpdate().withIPSetDescriptor(descriptor).withAction(ChangeAction.DELETE);
                updates.add(update);
                summery.get("removed").add(ip);
            } else {
                // dont need to block whats already blocked
                ipToBlock.remove(ip);
                summery.get("duplicate").add(ip);
            }
        });

        // Block the remaining ips
        ipToBlock.forEach(ip -> {
                String cidr = new SubnetUtils(ip, NETMASK_FOR_SINGLE_IP).getInfo().getCidrSignature();
                IPSetDescriptor descriptor = new IPSetDescriptor().withType(IPSetDescriptorType.IPV4).withValue(cidr);
                IPSetUpdate update = new IPSetUpdate().withIPSetDescriptor(descriptor).withAction(ChangeAction.INSERT);
                updates.add(update);
                summery.get("added").add(ip);
            }
        );

        if (! updates.isEmpty()) {
            GetChangeTokenResult token = awsWaf.getChangeToken(new GetChangeTokenRequest());

            // commit the changes
            awsWaf.updateIPSet(new UpdateIPSetRequest()
                    .withIPSetId(config.getRateLimitAutoBlackListIpSetId())
                    .withUpdates(updates)
                    .withChangeToken(token.getChangeToken()));
        }

        return summery;
    }

    /**
     * Retrieves the current serialized data of ips that we have blocked and when they violated the rate limit;
     * @return a map of ip addrs as Strings and the Date when we added them to the no no Map.
     */
    protected Map<String, ViolationMetaData> getCurrentlyBlockedIpsAndDateViolatedMap(String bucketName) {
        S3Object s3Object = null;
        try {
             s3Object = amazonS3.getObject(new GetObjectRequest(bucketName, SERIALIZED_DATA_FILE_NAME));
        } catch (AmazonS3Exception e) {
            if (e.getErrorCode().equals("NoSuchKey")) {
                return new HashMap<>();
            }
        }

        if (s3Object == null) {
            return new HashMap<>();
        }

        try {
            TypeReference<HashMap<String,ViolationMetaData>> typeRef = new TypeReference<HashMap<String,ViolationMetaData>>() {};
            return objectMapper.readValue(s3Object.getObjectContent(), typeRef);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize json data from previous runs", e);
        }
    }

    /**
     * Saves the violators data to s3 for future reference
     *
     * @param violators The map of violators.
     * @param bucketName The bucket to save the serialized data.
     */
    protected void saveCurrentViolators(Map<String, ViolationMetaData> violators, String bucketName) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(violators);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize violators data");
        }
        InputStream jsonStream = new ByteArrayInputStream(bytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        amazonS3.putObject(new PutObjectRequest(bucketName, SERIALIZED_DATA_FILE_NAME, jsonStream, metadata));
    }

    /**
     * Retrieves ip set info for a given ip set id and returns a list of subnet info objects
     *
     * @param ipSetId The IP Set Id to look up in AWS
     * @return A List of Subnet Info objects that contain the IP Cidr info on what is in the IP Set
     */
    protected List<SubnetUtils.SubnetInfo> getIpSet(String ipSetId) {
        List<SubnetUtils.SubnetInfo> ips = new LinkedList<>();

        GetIPSetResult result = awsWaf.getIPSet(new GetIPSetRequest().withIPSetId(ipSetId));
        result.getIPSet().getIPSetDescriptors().forEach(ipSetDescriptor -> {
            if (IPSetDescriptorType.IPV4.toString().equals(ipSetDescriptor.getType())) {
                SubnetUtils subnetUtils = new SubnetUtils(ipSetDescriptor.getValue());
                subnetUtils.setInclusiveHostCount(true);
                ips.add(subnetUtils.getInfo());
            }
        });

        return ips;
    }

    /**
     * Takes a log entry that is tab separated http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html#LogFileFormat
     * Will process the request entry and check to see if IP should be blacklisted
     * @param event The log event
     * @param reqIdCountMap the running count map, keeps track of requests per minute
     */
    protected void processRequest(CloudFrontLogEvent event, Map<String, Integer> reqIdCountMap) {
        // create a key out of the date plus the time to a minutes accuracy and the requester ip, and count the requests
        String requestKey = String.format("%s--%s--%s", event.getDateString(), event.getTimeString().substring(0, 5), event.getCIp());
        if (reqIdCountMap.containsKey(requestKey)) {
            reqIdCountMap.put(requestKey, reqIdCountMap.get(requestKey) + 1);
        } else {
            reqIdCountMap.put(requestKey, 1);
        }
    }

    /**
     * Process the map we created and ensure that keys in the map that have values greater than the rate limit per minute
     * get processed and dealt with.
     *
     * @param reqIdCountMap The map of request ids to request count, this assumes the requests are grouped by IPs combined with time to a minutes accuracy
     * @param params The params from the CloudFormation outputs
     * @return a map of ip addresses to violation meta data, containing ips that violated the rate limit
     */
    protected Map<String, ViolationMetaData> getCurrentViolators(Map<String, Integer> reqIdCountMap, CloudFrontLogHandlerConfig params) {
        Date now = new Date();
        Map<String, ViolationMetaData> violators = Maps.newHashMap();

        reqIdCountMap.entrySet().stream()
                .filter(entry -> entry.getValue() > params.getRequestPerMinuteLimit())
                .forEach(entry -> {
                    String ip = entry.getKey().split("--")[2];
                    if (violators.containsKey(ip)) {
                        ViolationMetaData metaData = violators.get(ip);
                        if (metaData.getMaxRate() < entry.getValue()) {
                            metaData.setMaxRate(entry.getValue());
                        }
                    } else {
                        violators.put(ip, new ViolationMetaData(now, entry.getValue()));
                    }
                });

        return violators;
    }

    /**
     * Goes through the white and black lists to check if an ip address from the access logs is a
     * valid candidate to add to the auto blacklist.
     *
     * @param doNotAutoBlockIpRangeSet The Ip Range Set of ips we do not want to black list
     * @param ipFromAccessLog The IP Address we are considering to automatically black list
     * @return A boolean of whether or not this processor should black list the ip.
     */
    protected boolean canAddToAutoBlackList(RangeSet<Integer> doNotAutoBlockIpRangeSet, String ipFromAccessLog) {
        SubnetUtils subnetUtils = new SubnetUtils(ipFromAccessLog, NETMASK_FOR_SINGLE_IP);
        Integer ipAsInteger = subnetUtils.getInfo().asInteger(ipFromAccessLog);
        return ! doNotAutoBlockIpRangeSet.contains(ipAsInteger);
    }
}
