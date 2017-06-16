# Cerberus Admin IAM Role

This template creates the admin IAM role to be used by the Lambda serverless components to call the Cerberus Management Service admin endpoints

## Deploy

After configuring a global profile, you can simply run.

    ./gradlew cerberus-admin-iam-role:deploy --stacktrace

After deploying, you can get the admin IAM role ARN as a CloudFormation export variable `CerberusAdminIamRoleARN` and the role name via export variable `CerberusAdminIamRole`.
