package com.nike.cerberus.lambda.waf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldju.commons.EnvUtils;
import com.nike.cerberus.client.auth.DefaultCerberusCredentialsProviderChain;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static okhttp3.ConnectionSpec.CLEARTEXT;
import static okhttp3.ConnectionSpec.MODERN_TLS;

public class CerberusMetadataLookup {

    private static final int DEFAULT_TIMEOUT = 60_000;
    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
    private static final String CERBERUS_TOKEN = "X-Cerberus-Token";
    private static final String EMAIL_SYMBOL = "@";

    /**
     * Modify "MODERN_TLS" to remove TLS v1.0 and 1.1
     */
    private static final ConnectionSpec TLS_1_2_OR_NEWER = new ConnectionSpec.Builder(MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build();

    private OkHttpClient createHttpClient() {

        List<ConnectionSpec> connectionSpecs = new ArrayList<>();
        connectionSpecs.add(TLS_1_2_OR_NEWER);

        connectionSpecs.add(CLEARTEXT);

        return new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .writeTimeout(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .readTimeout(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .connectionSpecs(connectionSpecs)
                .build();
    }

    /**
     * Obtains Cerberus Metadata which is a list of SDB summaries
     */
    public ArrayList<Map<String, String>> getCerberusMetadata(String cerberusUrl) {

        OkHttpClient httpClient = createHttpClient();
        HashMap result;
        String region = EnvUtils.getRequiredEnv("REGION");
        DefaultCerberusCredentialsProviderChain chain = new DefaultCerberusCredentialsProviderChain(cerberusUrl, region);

        try {
            Request request = new Request.Builder()
                .url(cerberusUrl + "/v1/metadata?limit=2000&offset=0")
                .addHeader(CERBERUS_TOKEN,chain.getCredentials().getToken())
                .get()
                .build();
            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            result = new ObjectMapper().readValue(responseBody, HashMap.class);
        } catch (IOException e) {
            throw new RuntimeException("I/O error while communicating with Cerberus", e);
        }

        ArrayList<Map<String, String>> sdbMetadata = (ArrayList<Map<String, String>>) result.get("safe_deposit_box_metadata");
        if (sdbMetadata.isEmpty()) {
            throw new NullPointerException("SDB Metadata is empty");
        }
        return sdbMetadata;
    }

    public ArrayList<String> searchCerberusMetadata(ArrayList<Map<String, String>> sdbMetadata, String sdbName, String principalName) {

        if (sdbMetadata == null) {
            throw new NullPointerException("SDB Metadata is empty");
        }

        ArrayList<String> owner = new ArrayList<>();

        for (Map<String, String> entry : sdbMetadata) {

            if (entry.get("name").equals(sdbName)) {
                owner.add(entry.get("owner"));
                if (entry.get("created_by").contains(EMAIL_SYMBOL)) owner.add(entry.get("created_by"));
                if (entry.get("last_updated_by").contains(EMAIL_SYMBOL)
                        && !entry.get("last_updated_by").equals(entry.get("created_by"))) {
                    owner.add(entry.get("last_updated_by"));
                }
                return owner;
            } else {
                if (entry.containsValue(principalName)) {
                    owner.add(entry.get("owner"));
                    if (entry.get("created_by").contains(EMAIL_SYMBOL)) owner.add(entry.get("created_by"));
                    if (entry.get("last_updated_by").contains(EMAIL_SYMBOL)
                            && !entry.get("last_updated_by").equals(entry.get("created_by"))) {
                        owner.add(entry.get("last_updated_by"));
                    }
                return owner;
                }
            }
        }

        owner.add("No owner found");
        return owner;
    }
}