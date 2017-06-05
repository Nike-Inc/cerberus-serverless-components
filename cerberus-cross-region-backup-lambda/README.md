# Cerberus Cross Region Backup Lambda

Deploying.

First Time: 
1. Create the Lambda VPC for us-east using cloudformation using [us-east-lambda-vpc.yaml](cerberus-lambda-vpc/us-east-lambda-vpc.yaml), taking note of its outputs.
    - The CF outputs the following
        - PrivateSubnetIds, these will be used in the profile step below. Please note that the Lambda service is not available in all AZs, so you wont be able to use all the subnets outputed by this, you should aim to have at least 3. Once you determine which AZs you cant use, you can delete them from the Cloudformation and re-run it to delete the subnets you do not need. Aim to have 3 so you are HA.
        - ElasticIpAddresses, these are the static IPs that your lambdas will be run from, these need to get added to the Manual Whitelist in the WAF for the Cerberus environment as this lambda violates the rate limit.
1. Deploy using `./gradlew clean cerberus-cross-region-backup-lambda:shadowJar cerberus-cross-region-backup-lambda:deploySam -Penv=[ENVIRONMENT]` making sure to create a new profile/[ENVIRONMENT].properties profile with the props from the profile/default.properties (the default security group for the above VPC is the only SG you should need)
1. In the AWS console take not of the Backup IAM Role ARN that was created by the stack for the backup lambda. This will be under stack resources.
1. Grab the root token using the CLI `cerberus -e foo -r us-west-2 view-config --config-path config/secrets.json`
1. Inside the target envrinments Cerberus Management Dashboard create an sdb called `cerberus cross region backup lambda` and a new vault path call `config` add the root token at the following key: `root_token`
1. Add the backup role ARN to the `cerberus cross region backup lambda` you created above with read permissions
1. Add the backup role ARN to the CMS properties as an admin role, ex `cerberus -e foo -r bar update-cms-config -Pcms.admin.roles=arn::xxxxxxxxxxxx`
1. Perform a rolling restart of CMS, you can do this safely with the CLI `cerberus -e foo -r bar --proxy-type SOCKS --proxy-host localhost --proxy-port 9000 reboot-cluster --stack-name CMS`

Notes: if you use us-east as your primary region then you probably dont want your backup in us-east and will need to modify the cloud formation to create your lambda vpc in a different region. You will probably want at least 2 AZ's for your lambda VPC

Future Deploys:

Just run `./gradlew clean cerberus-cross-region-backup-lambda:shadowJar cerberus-cross-region-backup-lambda:deploySam -Penv=[ENVIRONMENT]`
