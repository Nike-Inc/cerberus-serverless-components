package com.nike.cerberus.lambda.waf;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class CerberusMetadataLookupTest {

    private static final String ERROR_RESPONSE = "Test error response";
    private static final String mockResponseBodyString = "{\n" +
            "        \"has_next\" : false,\n" +
            "        \"next_offset\" : 0,\n" +
            "        \"limit\" : 2000,\n" +
            "        \"offset\" : 0,\n" +
            "        \"sdb_count_in_result\" : 1,\n" +
            "        \"total_sdbcount\" : 1,\n" +
            "        \"safe_deposit_box_metadata\" : [ {\n" +
            "            \"name\" : \"test sdb\",\n" +
            "            \"path\" : \"test/test-sdb/app\",\n" +
            "            \"category\" : \"Applications\",\n" +
            "            \"owner\" : \"Test.Owner\",\n" +
            "            \"description\" : \"test sdb\",\n" +
            "            \"created_ts\" : \"2019-05-13T20:13:56.799Z\",\n" +
            "            \"created_by\" : \"test.user@nike.com\",\n" +
            "            \"last_updated_ts\" : \"2019-05-13T20:13:56.799Z\",\n" +
            "            \"last_updated_by\" : \"arn:aws:iam::111111111111:role/example/role\",\n" +
            "            \"user_group_permissions\" : {  },\n" +
            "            \"iam_role_permissions\" : {\n" +
            "               \"arn:aws:iam::111111111111:role/example/role\" : \"read\"\n" +
            "          },\n" +
            "       \"data\" : null\n" +
            "      }]\n" +
            "}";

    private static final String mockResponseBodyMultiSdbString = "[ {\n" +
            "            \"name\" : \"test sdb\",\n" +
            "            \"path\" : \"test/first-test-sdb/app\",\n" +
            "            \"category\" : \"Applications\",\n" +
            "            \"owner\" : \"Test.Owner\",\n" +
            "            \"description\" : \"test sdb\",\n" +
            "            \"created_ts\" : \"2019-05-13T20:13:56.799Z\",\n" +
            "            \"created_by\" : \"test.user@nike.com\",\n" +
            "            \"last_updated_ts\" : \"2019-05-13T20:13:56.799Z\",\n" +
            "            \"last_updated_by\" : \"arn:aws:iam::111111111111:role/example/role\",\n" +
            "            \"user_group_permissions\" : {  },\n" +
            "            \"iam_role_permissions\" : {\n" +
            "               \"arn:aws:iam::111111111111:role/example/role\" : \"read\"\n" +
            "          },\n" +
            "       \"data\" : null\n" +
            "      }," +
            "      {\n" +
            "            \"name\" : \"test sdb 2\",\n" +
            "            \"path\" : \"test/test-sdb/app\",\n" +
            "            \"category\" : \"Applications\",\n" +
            "            \"owner\" : \"Test.Owner2\",\n" +
            "            \"description\" : \"test sdb 2\",\n" +
            "            \"created_ts\" : \"2019-05-13T20:13:56.799Z\",\n" +
            "            \"created_by\" : \"test.user2@nike.com\",\n" +
            "            \"last_updated_ts\" : \"2019-05-13T20:13:56.799Z\",\n" +
            "            \"last_updated_by\" : \"arn:aws:iam::111111111111:role/example2/role\",\n" +
            "            \"user_group_permissions\" : {  },\n" +
            "            \"iam_role_permissions\" : {\n" +
            "               \"arn:aws:iam::111111111111:role/example/role\" : \"read\"\n" +
            "          },\n" +
            "       \"data\" : null\n" +
            "      }]\n";

    private static final String emptyMetadataResponseBodyString = "{  \n" +
            "   \"has_next\":false,\n" +
            "   \"next_offset\":0,\n" +
            "   \"limit\":2000,\n" +
            "   \"offset\":0,\n" +
            "   \"sdb_count_in_result\":1723,\n" +
            "   \"total_sdbcount\":1723,\n" +
            "   \"safe_deposit_box_metadata\":[  \n" +
            "\n" +
            "   ]\n" +
            "}";

    private CerberusMetadataLookup cerberusMetadataLookup = new CerberusMetadataLookup();

//    @Test
//    public void test_get_metadata_successful() throws IOException {
//        MockWebServer mockWebServer = new MockWebServer();
//        mockWebServer.start();
//        final String cerberusUrl = "http://localhost:" + mockWebServer.getPort();
//
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(mockResponseBodyString));
//        ArrayList<Map<String, String>> sdbMetadata = cerberusMetadataLookup.getCerberusMetadata(cerberusUrl);
//        assertNotNull(sdbMetadata);
//    }

    @Test(expected = RuntimeException.class)
    public void test_get_metadata_bad_response() throws IOException {
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.start();
        final String cerberusUrl = "http://localhost:" + mockWebServer.getPort();

        mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(ERROR_RESPONSE));
        ArrayList<Map<String, String>> sdbMetadata = cerberusMetadataLookup.getCerberusMetadata(cerberusUrl);
    }

    @Test(expected = RuntimeException.class)
    public void test_get_metadata_empty_response_body() throws IOException {
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.start();
        final String cerberusUrl = "http://localhost:" + mockWebServer.getPort();

        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
        ArrayList<Map<String, String>> sdbMetadata = cerberusMetadataLookup.getCerberusMetadata(cerberusUrl);
    }

//    @Test(expected = NullPointerException.class)
//    public void test_get_metadata_empty_metadata() throws IOException {
//        MockWebServer mockWebServer = new MockWebServer();
//        mockWebServer.start();
//        final String cerberusUrl = "http://localhost:" + mockWebServer.getPort();
//
//        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(emptyMetadataResponseBodyString));
//        ArrayList<Map<String, String>> sdbMetadata = cerberusMetadataLookup.getCerberusMetadata(cerberusUrl);
//    }

    @Test
    public void test_search_cerberus_metadata_successful() throws IOException {
        HashMap responseMap = new ObjectMapper().readValue(mockResponseBodyString, HashMap.class);
        ArrayList<Map<String, String>> sdbMetadata = (ArrayList<Map<String, String>>) responseMap.get("safe_deposit_box_metadata");
        String sdbName = "test-sdb";
        String principalName = "arn:aws:iam::111111111111:role/example2/role";

        ArrayList<String> results = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, sdbName, principalName);
        assertTrue(results.contains("Test.Owner"));
        assertTrue(results.contains("test.user@nike.com"));
        assertFalse(results.contains("arn:aws:iam::111111111111:role/example/role"));
    }

    @Test
    public void test_search_cerberus_metadata_multiple_sdb_contains_name_successful() throws IOException {
        ArrayList<Map<String, String>> sdbMetadata = (ArrayList<Map<String, String>>) new ObjectMapper().readValue(mockResponseBodyMultiSdbString, ArrayList.class);

        String sdbName = "test-sdb";
        String principalName = "arn:aws:iam::111111111111:role/example2/role";

        ArrayList<String> results = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, sdbName, principalName);
        assertTrue(results.contains("Test.Owner2"));
        assertTrue(results.contains("test.user2@nike.com"));
        assertFalse(results.contains("arn:aws:iam::111111111111:role/example2/role"));
    }

    @Test
    public void test_search_cerberus_metadata_successful_with_unknown_sdb_name() throws IOException {
        HashMap responseMap = new ObjectMapper().readValue(mockResponseBodyString, HashMap.class);
        ArrayList<Map<String, String>> sdbMetadata = (ArrayList<Map<String, String>>) responseMap.get("safe_deposit_box_metadata");
        String sdbName = "_unknown";
        String principalName = "arn:aws:iam::111111111111:role/example/role";

        ArrayList<String> results = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, sdbName, principalName);
        assertTrue(results.contains("Test.Owner"));
        assertTrue(results.contains("test.user@nike.com"));
        assertFalse(results.contains("arn:aws:iam::111111111111:role/example/role"));
    }

    @Test(expected = NullPointerException.class)
    public void test_search_cerberus_metadata_parameter_null() throws IOException {
        String sdbName = "test sdb";
        String principalName = "arn:aws:iam::111111111111:role/example/role";

        ArrayList<String> results = cerberusMetadataLookup.searchCerberusMetadata(null, sdbName, principalName);
        assertNotNull(results);
    }

    @Test
    public void test_search_cerberus_metadata_parameters_empty() throws IOException {
        HashMap result = new ObjectMapper().readValue(mockResponseBodyString, HashMap.class);
        ArrayList<Map<String, String>> sdbMetadata = (ArrayList<Map<String, String>>) result.get("safe_deposit_box_metadata");

        ArrayList<String> results = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, "", "");
        assertTrue(results.contains("No owner found"));
    }

    @Test
    public void test_search_cerberus_metadata_not_successful() throws IOException {
        HashMap responseMap = new ObjectMapper().readValue(mockResponseBodyString, HashMap.class);
        ArrayList<Map<String, String>> sdbMetadata = (ArrayList<Map<String, String>>) responseMap.get("safe_deposit_box_metadata");
        String sdbName = "none";
        String principalName = "arn:aws:iam::00000000000:role/example/role";

        ArrayList<String> results = cerberusMetadataLookup.searchCerberusMetadata(sdbMetadata, sdbName, principalName);
        assertTrue(results.contains("No owner found"));
    }
}