# Cerberus Cross Region Backup Lambda

Deploying.

First Time: 
1. Create the Lambda VPC for us-east using cloudformation using [us-east-lambda-vpc.yaml](cerberus-lambda-vpc/us-east-lambda-vpc.yaml), taking note of its outputs.
    - The CF outputs the following
        - PrivateSubnetIds, these will be used in the profile step below. Please note that the Lambda service is not available in all AZs, so you wont be able to use all the subnets outputed by this, you should aim to have at least 3. Once you determine which AZs you cant use, you can delete them from the Cloudformation and re-run it to delete the subnets you do not need. Aim to have 3 so you are HA.
        - ElasticIpAddresses, these are the static IPs that your lambdas will be run from, these need to get added to the Manual Whitelist in the WAF for the Cerberus environment as this lambda violates the rate limit.
1. Deploy using `./gradlew clean cerberus-cross-region-backup-lambda:shadowJar cerberus-cross-region-backup-lambda:deploySam -Penv=[ENVIRONMENT]` making sure to create a new profile/[ENVIRONMENT].properties profile with the props from the profile/default.properties (the default security group for the above VPC is the only SG you should need)
1. Ensure to generate new CMS config adding the IAM Role ARN for the IAM Role generated from the stack created by the above command adding it to the cms.admin.roles -P prop ex: `-Pcms.admin.roles=arn:aws:iam::111111111:role/test-cerberus-cross-regio-CerberusCrossRegionBacku-1W93J2KE1BUKJ`
1. Inside the target CMS, using the S3 downloader python script, add the root token to and sdb to be available on the following path: `/v1/secret/app/cerberus-cross-region-backup-lambda/config` with the root token at the following key: root_token

Notes: if you use us-east as your primary region then you probably dont want your backup in us-east and will need to modify the cloud formation to create your lambda vpc in a different region. You will probably want at least 2 AZ's for your lambda VPC

Future Deploys:

Just run `./gradlew clean cerberus-cross-region-backup-lambda:shadowJar cerberus-cross-region-backup-lambda:deploySam -Penv=[ENVIRONMENT]`