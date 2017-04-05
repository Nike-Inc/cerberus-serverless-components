package com.nike.cerberus

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.s3.AmazonS3EncryptionClient
import com.amazonaws.services.s3.model.CryptoConfiguration
import com.amazonaws.services.s3.model.KMSEncryptionMaterialsProvider
import com.amazonaws.services.s3.model.ObjectMetadata
import com.nike.cerberus.util.EnvVarUtils
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.internal.LazyMap
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.log4j.Logger

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

/**
 * Handler for the Cerberus cross region backup lambda
 */
class CerberusCrossRegionBackupHandler {

    private static final int DEFAULT_TIMEOUT = 10

    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS

    static Logger log = Logger.getLogger(getClass())

    static def CERBERUS_URL_ENV_VAR_KEY = 'CERBERUS_URL'
    static def ACCOUNT_ID_ENV_VAR_KEY = 'ACCOUNT_ID'
    static def ROLE_NAME_ENV_VAR_KEY = 'ROLE_NAME'
    static def REGION_ENV_VAR_KEY = 'REGION'
    static def BUCKET_ENV_VAR_KEY = 'BUCKET'
    static def KMS_KEY_ENV_VAR_KEY = 'KMS_KEY_ID'

    AWSCredentialsProvider credentialsProvider

    /**
     * Constructor for local development, allowing us to assume a role to run as and an environment to use when
     * interacting with the AWS sdk.
     *
     * @param roleToAssume the role to assume and use in the credentials chain for AWS clients
     * @param region
     */
    CerberusCrossRegionBackupHandler(String roleToAssume) {
        credentialsProvider = new STSProfileCredentialsServiceProvider(
                new RoleInfo().withRoleArn(roleToAssume)
                        .withRoleSessionName(UUID.randomUUID().toString()))
    }

    CerberusCrossRegionBackupHandler() {
        credentialsProvider = new DefaultAWSCredentialsProviderChain()
    }

    void handle() {
        log.info 'Checking for required environmental Variables'
        def url = EnvVarUtils.getRequiredEnvVar(CERBERUS_URL_ENV_VAR_KEY)
        def accountId = EnvVarUtils.getRequiredEnvVar(ACCOUNT_ID_ENV_VAR_KEY)
        def rolename = EnvVarUtils.getRequiredEnvVar(ROLE_NAME_ENV_VAR_KEY)
        def regionString = EnvVarUtils.getRequiredEnvVar(REGION_ENV_VAR_KEY)
        def backupKmsCMKId = EnvVarUtils.getRequiredEnvVar(KMS_KEY_ENV_VAR_KEY)
        def backupBucket = EnvVarUtils.getRequiredEnvVar(BUCKET_ENV_VAR_KEY)

        log.info 'Creating AWS and Cerberus Clients'
        OkHttpClient client = new OkHttpClient.Builder()
                .hostnameVerifier(new NoopHostnameVerifier()) // talking straight to the ELB, skip hostname ver
                .connectTimeout(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .writeTimeout(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .readTimeout(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .build()

        Region region = Region.getRegion(Regions.fromName(regionString))

        AWSKMSClient kmsClient = new AWSKMSClient(credentialsProvider).withRegion(region)

        KMSEncryptionMaterialsProvider materialProvider =
                new KMSEncryptionMaterialsProvider(backupKmsCMKId)

        AmazonS3EncryptionClient s3EncryptionClient =
                new AmazonS3EncryptionClient(credentialsProvider,
                        materialProvider,
                        new CryptoConfiguration().withAwsKmsRegion(region))
                        .withRegion(region)

        log.info 'Loading secure runtime config from Cerberus'
        String authToken = getCerberusToken(kmsClient, client)
        String adminReadToken = getAdminReadToken(authToken, client)

        log.info 'Backing up data from Cerberus'
        def now = new Date()
        String prefix = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(now)
        List<LazyMap> sdbMetadataList = getSDBMetaData(client, authToken)


        MetaObject metaObject = new MetaObject()
        for (def sdb : sdbMetadataList) {
            log.info("Backing up ${sdb?.name}")
            def vaultData = recurseVault(client, sdb.path as String, adminReadToken)
            sdb.put('data', vaultData)
            def key = "${sdb.name.toLowerCase().replaceAll(/\W+/, '-')}"
            saveDataToS3(s3EncryptionClient, sdb, backupBucket, prefix, key)
            metaObject = processMetadata(sdb, metaObject)
        }

        // save metadata
        def metadata = [
                cerberusUrl: url,
                backupDate: now,
                lambdaBackupAccountId: accountId,
                lambdaBackupRegion: regionString,
                numberOfSdbs: sdbMetadataList.size(),
                numberOfKeyValuePairs: metaObject.numberOfKeyValuePairs,
                numberOfDataNodes: metaObject.numberOfDataNodes,
                numberOfUniqueOwnerGroups: metaObject.uniqueOwnerGroups.size(),
                numberOfUniqueIamRoles: metaObject.uniqueIamRoles.size(),
                numberOfUniqueNonOwnerGroups: metaObject.uniqueNonOwnerGroups.size()
        ]
        def key = "cerberus-backup-metadata.json"
        log.info("Saving metadata: ${metadata} to ${prefix}/${key}")
        saveDataToS3(s3EncryptionClient, metadata, backupBucket, prefix, key)
    }

    /**
     * Method to keep track of metadata
     */
    def processMetadata(def sdb, final MetaObject immutableMeta) {
        def newMeta = new MetaObject([
                numberOfKeyValuePairs: immutableMeta.numberOfKeyValuePairs,
                numberOfDataNodes: immutableMeta.numberOfDataNodes,
                uniqueOwnerGroups: immutableMeta.uniqueOwnerGroups,
                uniqueIamRoles: immutableMeta.uniqueIamRoles,
                uniqueNonOwnerGroups: immutableMeta.uniqueNonOwnerGroups
        ])

        newMeta.uniqueOwnerGroups.add(sdb?.owner)
        sdb?.'iam_role_permissions'?.each {
            newMeta.uniqueIamRoles.add(it?.key)
        }
        sdb?.'user_group_permissions'?.each {
            newMeta.uniqueNonOwnerGroups.add(it?.key)
        }

        Map<String, Map<String, String>> data = sdb?.data
        newMeta.numberOfDataNodes = newMeta.numberOfDataNodes + ( data?.size() ? data.size() : 0 )
        data.each { path, secrets ->
            newMeta.numberOfKeyValuePairs = newMeta.numberOfKeyValuePairs + ( secrets?.size() ? secrets.size() : 0 )
        }
        return newMeta
    }

    /**
     * Paginiates and retrieves all the metadata from CMS
     * @param cerberusAuthenticationToken, the auth token for the IAM role assigned to this lambda, it must be
     * generated from an IAM role that is an admin arn so that it has access to the admin metadata endpoint
     * @return
     */
    static def getSDBMetaData(OkHttpClient client, String cerberusAuthenticationToken) {
        def metadataList = []
        // paginate over all SDBs
        String offset = '0'
        while({
            URL baseUrl = new URL(System.getenv(CERBERUS_URL_ENV_VAR_KEY))

            Request request = new Request.Builder()
                    .url(
                        new HttpUrl.Builder()
                            .scheme(baseUrl.protocol)
                            .host(baseUrl.host)
                            .addPathSegments('v1/metadata')
                            .addQueryParameter('limit', '100')
                            .addQueryParameter('offset', offset)
                            .build()
                    )
                    .addHeader('X-Vault-Token', cerberusAuthenticationToken)
                    .get()
                    .build()

            Response response = client.newCall(request).execute()
            def resp = new JsonSlurper().parseText(response.body().string())

            resp.'safe_deposit_box_metadata'.each { Map sdbMetaData ->
                metadataList.add(sdbMetaData)
            }

            offset = resp.next_offset
            resp.has_next == true
        }()); // don't delete this semicolin

        return metadataList
    }

    /**
     * Recurses a Vault path for data.
     *
     * @param cerberusClient The Cerberus Vault client
     * @param path The path to recurse
     * @param token The Vault auth token to use
     * @return Map of Vault path Strings to Maps of String, String containing the secret kv pairs
     */
    def recurseVault(OkHttpClient cerberusClient, String path, String token, Map<String, Map<String, String>> data = [:]) {
        getKeys(cerberusClient, path, token).each { key ->
            if (key.endsWith('/')) {
                recurseVault(cerberusClient, "$path$key", token, data)
            } else {
                data.put("$path$key", getData(cerberusClient, "$path$key", token))
            }
        }
        return data
    }

    /**
     * Lists keys for a vault path.
     *
     * @param cerberusClient The cerberus vault client
     * @param path The path in Vault to list keys for
     * @param token The Vault auth token
     * @return List of keys, sub folders with have trailing /
     */
    List<String> getKeys(OkHttpClient client, path, String token) {
        URL baseUrl = new URL(System.getenv(CERBERUS_URL_ENV_VAR_KEY))

        Request request = new Request.Builder()
                .url(
                    new HttpUrl.Builder()
                            .scheme(baseUrl.protocol)
                            .host(baseUrl.host)
                            .addPathSegments("/v1/secret/${path}")
                            .addQueryParameter('list', 'true')
                            .build()
                )
                .addHeader('X-Vault-Token', token)
                .get()
                .build()
        Response response = client.newCall(request).execute()

        if (response.code() == 404) {
            response.body().close()
            log.warn("/v1/secret/${path} returned 404, probably an empty SDB")
            return []
        }

        def resp = new JsonSlurper().parseText(response.body().string())
        return resp?.data?.keys
    }

    /**
     * Downloads Vault data for a given path.
     *
     * @param cerberusClient The cerberus vault client
     * @param path The path of data to download
     * @param token The Vault auth token
     * @return The data map
     */
    static Map<String, String> getData(OkHttpClient client, path, token) {
        Request request = new Request.Builder()
                .url(System.getenv(CERBERUS_URL_ENV_VAR_KEY) + "/v1/secret/${path}")
                .addHeader('X-Vault-Token', token)
                .get()
                .build()

        Response response = client.newCall(request).execute()
        def resp = new JsonSlurper().parseText(response.body().string())
        return resp?.data
    }

    /**
     * Using an S3 Encryption client saves the sdb data to the backup bucket / kms key
     * @param s3EncryptionClient The Encryption cleint
     * @param sdb The sdb data to back up
     * @param bucket The bucket to store the data
     * @param prefix The prefix / virtual folder to store the encrypted json
     */
    static void saveDataToS3(AmazonS3EncryptionClient s3EncryptionClient, def sdb, String bucket, String prefix, String key) {
        String json = new JsonBuilder(sdb).toString()
        byte[] content = json.getBytes(Charset.forName('UTF-8'))
        ByteArrayInputStream contentAsStream = new ByteArrayInputStream(content)
        ObjectMetadata md = new ObjectMetadata()
        md.setContentLength(content.length)
        s3EncryptionClient.putObject(bucket, "$prefix/$key", contentAsStream, md)
    }

    /**
     * read the admin read token from cerberus
     */
    static def getAdminReadToken(String token, OkHttpClient client) {
        Request request = new Request.Builder()
                .url(System.getenv(CERBERUS_URL_ENV_VAR_KEY) + '/v1/secret/app/cerberus-cross-region-backup-lambda/config')
                .addHeader('X-Vault-Token', token)
                .get()
                .build()

        Response response = client.newCall(request).execute()
        def resp = new JsonSlurper().parseText(response.body().string())
        return resp.data.root_token
    }

    /**
     * Authenticates with cerberus
     * @return Cerberus Vault Token
     */
    static def getCerberusToken(AWSKMSClient kmsClient, OkHttpClient client) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8")

        RequestBody body = RequestBody.create(JSON,
                new JsonBuilder([
                        account_id: System.getenv(ACCOUNT_ID_ENV_VAR_KEY),
                        role_name: System.getenv(ROLE_NAME_ENV_VAR_KEY),
                        region: System.getenv(REGION_ENV_VAR_KEY)
                ]).toString())

        Request request = new Request.Builder()
                .url(System.getenv(CERBERUS_URL_ENV_VAR_KEY) + '/v1/auth/iam-role')
                .post(body)
                .build()
        Response response = client.newCall(request).execute()
        def authPayload = new JsonSlurper().parseText(response.body().string())

        String encryptedAuthData = authPayload.auth_data
        def authResp = kmsClient.decrypt(new DecryptRequest()
                .withCiphertextBlob(ByteBuffer.wrap(encryptedAuthData.decodeBase64())))
        def respString = new String(authResp.getPlaintext().array())
        def authData = new JsonSlurper().parseText(respString)

        return authData.client_token
    }
    
    class MetaObject {
        Integer numberOfKeyValuePairs = 0
        Integer numberOfDataNodes = 0
        Set<String> uniqueOwnerGroups = []
        Set<String> uniqueIamRoles = []
        Set<String> uniqueNonOwnerGroups = []
    }
}
