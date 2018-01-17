package com.nike.cerberus.lambda.waf;

/**
 * Simple DTO for CloudFront log entry
 *
 * http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html#LogFileFormat
 */
public class CloudFrontLogEvent {

    private final String[] data;

    public CloudFrontLogEvent(String logEntry) {
        if (logEntry == null || logEntry.equals("")) {
            throw new IllegalArgumentException("You must supply a valid non empty CloudFront log entry, see http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html#LogFileFormat");
        }
        data = logEntry.split("\\t");

        if (data.length != 26) {
            throw new IllegalArgumentException("You must supply a valid non empty CloudFront log entry, see http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html#LogFileFormat");
        }
    }

    /**
     * @return The date on which the event occurred in the format yyyy-mm-dd, for example, 2015-06-30.
     * The date and time are in Coordinated Universal Time (UTC).
     */
    public String getDateString() {
        return data[0];
    }

    /**
     * @return The time when the CloudFront server finished responding to the request (in UTC), for example, 01:42:39.
     */
    public String getTimeString() {
        return data[1];
    }

    /**
     * @return The edge location that served the request. Each edge location is identified by a three-letter code and
     * an arbitrarily assigned number, for example, DFW3. The three-letter code typically corresponds with the
     * International Air Transport Association airport code for an airport near the edge location. (These abbreviations
     * might change in the future.) For a list of edge locations, see the Amazon CloudFront detail page,
     * http://aws.amazon.com/cloudfront.
     */
    public String getXEdgeLocation() {
        return data[2];
    }

    /**
     * @return The total number of bytes that CloudFront served to the viewer in response to the request,
     * including headers, for example, 1045619.
     */
    public Integer getScBytes() {
        return Integer.valueOf(data[3]);
    }

    /**
     * @return The IP address of the viewer that made the request, for example, 192.0.2.183. If the viewer used an
     * HTTP proxy or a load balancer to send the request, the value of c-ip is the IP address of the proxy or
     * load balancer. See also X-Forwarded-For in field 20.
     */
    public String getCIp() {
        return data[4];
    }

    /**
     * @return The HTTP access method: DELETE, GET, HEAD, OPTIONS, PATCH, POST, or PUT.
     */
    public String getCsMethod() {
        return data[5];
    }

    /**
     * @return The domain name of the CloudFront distribution, for example, d111111abcdef8.cloudfront.net.
     */
    public String getCsHost() {
        return data[6];
    }

    /**
     * @return The portion of the URI that identifies the path and object, for example, /images/daily-ad.jpg.
     */
    public String getCsUriStem() {
        return data[7];
    }

    /**
     * @return One of the following values:
     *      An HTTP status code (for example, 200). For a list of HTTP status codes, see RFC 2616, Hypertext Transfer
     *      Protocol—HTTP 1.1, section 10, Status Code Definitions. For more information, see How CloudFront Processes
     *      and Caches HTTP 4xx and 5xx Status Codes from Your Origin.
     *
     *      000, which indicates that the viewer closed the connection (for example, closed the browser tab) before
     *      CloudFront could respond to a request.
     */
    public String getScStatus() {
        return data[8];
    }

    /**
     * @return The name of the domain that originated the request. Common referrers include search engines,
     * other websites that link directly to your objects, and your own website.
     */
    public String getCsRefeerer() {
        return data[9];
    }

    /**
     * @return The value of the User-Agent header in the request. The User-Agent header identifies the source of the
     * request, such as the type of device and browser that submitted the request and, if the request came from a
     * search engine, which search engine. For more information, see User-Agent Header.
     */
    public String getcsUserAgent() {
        return data[10];
    }

    /**
     * @return The query string portion of the URI, if any. When a URI doesn't contain a query string,
     * the value of cs-uri-query is a hyphen (-).
     */
    public String getCsUriQuery() {
        return data[11];
    }

    /**
     * @return The cookie header in the request, including name-value pairs and the associated attributes.
     * If you enable cookie logging, CloudFront logs the cookies in all requests regardless of which cookies you choose
     * to forward to the origin: none, all, or a whitelist of cookie names. When a request doesn't include a cookie
     * header, the value of cs(Cookie) is a hyphen (-).
     */
    public String getCsCookie() {
        return data[12];
    }

    /**
     * @return How CloudFront classified the response after the last byte left the edge location. In some cases, the
     * result type can change between the time that CloudFront is ready to send the response and the time that
     * CloudFront has finished sending the response. For example, in HTTP streaming, suppose CloudFront finds a
     * segment in the edge cache. The value of x-edge-response-result-type, the result type immediately before
     * CloudFront begins to respond to the request, is Hit. However, if the user closes the viewer before CloudFront
     * has delivered the entire segment, the final result type—the value of x-edge-result-type—changes to Error.
     */
    public String getXEdgeResultType() {
        return data[13];
    }

    /**
     * @return An encrypted string that uniquely identifies a request.
     */
    public String getXEdgeRequestID() {
        return data[14];
    }

    /**
     * @return The value that the viewer included in the Host header for this request.
     *
     * This is the domain name in the request
     */
    public String getHostHeader() {
        return data[15];
    }

    /**
     * @return The protocol that the viewer specified in the request, either http or https.
     */
    public String getCsProtocal() {
        return data[16];
    }

    /**
     * @return The number of bytes of data that the viewer included in the request (client to server bytes),
     * including headers.
     */
    public String getCsBytes() {
        return data[17];
    }

    /**
     * @return The number of seconds (to the thousandth of a second, for example, 0.002) between the time that a
     * CloudFront edge server receives a viewer's request and the time that CloudFront writes the last byte of the
     * response to the edge server's output queue as measured on the server. From the perspective of the viewer, the
     * total time to get the full object will be longer than this value due to network latency and TCP buffering.
     */
    public String getTimeTaken() {
        return data[18];
    }

    /**
     * @return If the viewer used an HTTP proxy or a load balancer to send the request, the value of c-ip in field 5
     * is the IP address of the proxy or load balancer. In that case, x-forwarded-for is the IP address of the
     * viewer that originated the request.
     *
     * If the viewer did not use an HTTP proxy or a load balancer, the value of x-forwarded-for is a hyphen (-).
     */
    public String getXForwardedFor() {
        return data[19];
    }

    /**
     * @return When cs-protocol in field 17 is https, the SSL protocol that the client and CloudFront negotiated for
     * transmitting the request and response. When cs-protocol is http, the value for ssl-protocol is a hyphen (-).
     */
    public String getSslProtocol() {
        return data[20];
    }

    /**
     * @return When cs-protocol in field 17 is https, the SSL cipher that the client and CloudFront negotiated for
     * encrypting the request and response. When cs-protocol is http, the value for ssl-cipher is a hyphen (-).
     */
    public String getSslCipher() {
        return data[21];
    }

    /**
     * @return How CloudFront classified the response just before returning the response to the viewer.
     * See also x-edge-result-type in field 14.
     */
    public String getXEdgeResponseResultType() {
        return data[22];
    }

    /**
     * @return The HTTP version that the viewer specified in the request.
     * Possible values include HTTP/0.9, HTTP/1.0, HTTP/1.1, and HTTP/2.0.
     */
    public String getCsProtocolVersion() {
        return data[23];
    }
}
