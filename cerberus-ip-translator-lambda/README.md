# Cerberus Ip Translator Lambda

Lambda for looking up IPs that were rate limited and posting metadata about them into slack


## Building

To build and deploy the fat jar required for Lambda run: 

```./gradlew cerberus-ip-translator-lambda:sJ cerberus-ip-translator-lambda:deploySam```
