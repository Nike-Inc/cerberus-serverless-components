package com.nike.cerberus.lambda.waf;

import com.google.common.base.Splitter;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple DTO for an Application Load Balancer access log entry
 *
 * Elastic Load Balancing logs requests on a best-effort basis. We recommend that you use access logs to understand
 * the nature of the requests, not as a complete accounting of all requests.
 *
 * The log entry for a particular request might be delivered long after the request was actually processed and, in rare
 * cases, a log entry might not be delivered at all. When a log entry is omitted from access logs, the number of entries
 * in the access logs won't match the usage that appears in the AWS usage and billing reports.
 *
 * http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
 */
public class AppLoadBalancerLogEvent {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /* Magic regex to split values on spaces, EXCEPT if the value is wrapped in quotes
     *
     * Ex.  foo "baz foobar" bar => [foo, "baz foobar", bar]
     *
     * https://stackoverflow.com/questions/1757065/java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
    */
    private static final Pattern LOG_SPLIT_PATTERN = Pattern.compile(" (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

    private static final int NUM_LOG_ENTRY_PARTS = 20;

    private static final String HTTP_METHOD_KEY = "httpMethod";

    private static final String FULL_URL_KEY = "url";

    private static final String PROTOCOL_KEY = "protocol";

    private static final String HOSTNAME_KEY = "host";

    private static final String REQUEST_PORT_KEY = "port";

    private static final String REQUEST_URI_KEY = "uri";

    private static final String HTTP_VERSION_KEY = "httpVersion";

    // regex for the "request" field in the ALB access log  (format: "GET https://cerberus.oss.nike.com:443/dashboard HTTP/2.0")
    private static final String REQUEST_FIELD_PATTERN_STR = String.format(
            "(?<%s>.*) (?<%s>(?<%s>.*)://(?<%s>.*):(?<%s>\\d*)(?<%s>/.*)) (?<%s>.*)",
            HTTP_METHOD_KEY,
            FULL_URL_KEY,
            PROTOCOL_KEY,
            HOSTNAME_KEY,
            REQUEST_PORT_KEY,
            REQUEST_URI_KEY,
            HTTP_VERSION_KEY);

    private static final Pattern REQUEST_FIELD_PATTERN = Pattern.compile(REQUEST_FIELD_PATTERN_STR);

    private final List<String> data;

    public AppLoadBalancerLogEvent(String logEntry) {
        if (logEntry == null || logEntry.equals("")) {
            throw new IllegalArgumentException("You must supply a valid non empty ALB access log entry, see " +
                    "http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html");
        }

        data = Splitter.on(LOG_SPLIT_PATTERN).splitToList(logEntry);
        if (data.size() != NUM_LOG_ENTRY_PARTS) {
            throw new IllegalArgumentException("You must supply a valid non empty ALB access log entry, see " +
                    "http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html");
        }
    }

    /**
     * @return The type of request or connection such (e.g. http, https)
     *
     * h2  => HTTP/2 over SSL/TLS
     * wss => WebSockets over SSL/TLS
     */
    public String getRequestType() {
        return data.get(0);
    }

    /**
     * @return The time when the Application Load Balancer finished responding to the request, in
     * ISO 8601 format (e.g. 2017-10-02T17:48:24.305799Z).
     */
    public DateTime getDateTime() {
        String dateTimeStr = data.get(1);
        return DateTime.parse(dateTimeStr);
    }

    /**
     * @return The AWS resource ID of the load balancer that handled this request
     */
    public String getLoadBalancerResourceId() {
        return data.get(2);
    }

    /**
     * @return The IP address of the client that made this request
     */
    public String getRequestingClientIp() {
        String clientIpAndPortStr = data.get(3);
        String[] clientIpAndPort = clientIpAndPortStr.split(":");
        return clientIpAndPort[0];
    }

    /**
     * @return The port on which the client made this request
     */
    public String getRequestingClientPort() {
        String clientIpAndPortStr = data.get(3);
        String[] clientIpAndPort = clientIpAndPortStr.split(":");
        return clientIpAndPort[1];
    }

    /**
     * @return The IP address of the target that processed this request
     *
     * If this value is "-", then either the client did not send a full request, or
     * the AWS WAF blocked the request.
     */
    public String getTargetIp() {
        String targetIpAndPortStr = data.get(4);
        if (targetIpAndPortStr.equals("-")) {
            return null;
        }

        String[] targetIpAndPort = targetIpAndPortStr.split(":");
        return targetIpAndPort[0];
    }

    /**
     * @return The port on which the target processed this request
     *
     * If this value is "-", then either the client did not send a full request, or
     * the AWS WAF blocked the request.
     */
    public String getTargetPort() {
        String targetIpAndPortStr = data.get(4);
        if (targetIpAndPortStr.equals("-")) {
            logger.warn("Target port is empty. Either the client did not send a full request or the AWS WAF blocked the request");
            return null;
        }

        String[] targetIpAndPort = targetIpAndPortStr.split(":");
        return targetIpAndPort[1];
    }

    /**
     * @return The total time elapsed (in seconds) from the time the load balancer received the request until the time
     * the load balancer sent the request to target
     *
     * If this value is -1, then the load balancer couldn't send the request to a target. This can happen if the
     * target closes the connection before the idle timeout, or if the client sends a malformed request.
     */
    public String getRequestProcessingTime() {
        return data.get(5);
    }

    /**
     * @return The total time elapsed (in seconds) from the time the load balancer sent the request to a target until
     * the target started to send the response headers.
     *
     * If this value is -1, then the load balancer couldn't send the request to a target. This can happen if the
     * target closes the connection before the idle timeout, or if the client sends a malformed request.
     */
    public String getTargetProcessingTime() {
        return data.get(6);
    }

    /**
     * @return The total time elapse (in seconds) from the time the load balancer received the response header from the
     * target until it started to send the response to the client. This includes both the queuing time at the load
     * balancer and the connection acquisition time from the load balancer to the client
     *
     * If this value is -1, then the load balancer couldn't send the request to a target. This can happen if the
     * target closes the connection before the idle timeout, or if the client sends a malformed request.     *
     */
    public String getResponseProcessingTime() {
        return data.get(7);
    }

    /**
     * @return The status code of the response from the load balancer
     */
    public String getLoadBalancerStatusCode() {
        return data.get(8);
    }

    /**
     * @return The status code of the response from the target.
     *
     * If this value is "-", then a connection to the target could not be established, or the target did not send a response
     */
    public String getTargetStatusCode() {
        return data.get(9);
    }

    /**
     * @return The total number of bytes in the request (in bytes) received from the client
     */
    public String getBytesReceived() {
        return data.get(10);
    }

    /**
     * @return The total number of bytes sent in response to the client's request
     */
    public String getBytesSent() {
        return data.get(11);
    }

    /**
     * @return The HTTP request method: DELETE, GET, HEAD, OPTIONS, PATCH, POST, or PUT.
     */
    public String getHttpMethod() {
        return getValueFromRequestField(HTTP_METHOD_KEY);
    }

    /**
     * @return The full request URL
     */
    public String getRequestUrl() {
        return getValueFromRequestField(FULL_URL_KEY);
    }

    /**
     * @return The hostname to which the request was sent
     */
    public String getHostname() {
        return getValueFromRequestField(HOSTNAME_KEY);
    }

    /**
     * @return The port on which the request was made
     */
    public String getRequestPort() {
        return getValueFromRequestField(REQUEST_PORT_KEY);
    }

    /**
     * @return The URI of the request
     */
    public String getRequestUri() {
        return getValueFromRequestField(REQUEST_URI_KEY);
    }

    /**
     * @return The HTTP version used in the request (e.g. HTTP/2.0)
     */
    public String getHttpVersion() {
        return getValueFromRequestField(HTTP_VERSION_KEY);
    }

    /**
     * @return The User-Agent string that identifies the client that originated the request
     *
     * Consists of one or more product identifiers, product[/version]. If the string is longer than 8 KB, it is truncated.
     */
    public String getUserAgent() {
        return data.get(13);
    }

    /**
     * @return The SSL cipher
     *
     * If this value is "-", then client connection negotiation was unsuccessful or the call was made over HTTP
     */
    public String getSslCipher() {
        return data.get(14);
    }

    /**
     * @return The SSL Protocol
     *
     * If this value is "-", then client connection negotiation was unsuccessful or the call was made over HTTP
     */
    public String getSslProtocol() {
        return data.get(15);
    }

    /**
     * @return The ARN of the target group that handled the request
     */
    public String getTargetGroupArn() {
        return data.get(16);
    }

//    /**
//     * @return How CloudFront classified the response just before returning the response to the viewer.
//     * See also x-edge-result-type in field 14.
//     */
//    public String getXAmazonTraceId() {
//        return data.get(17);
//    }

    private String getValueFromRequestField(String valueName) {
        Matcher request = REQUEST_FIELD_PATTERN.matcher(data.get(12));
        if (! request.find()) {
            return null;
        }

        return request.group(valueName);
    }
}
