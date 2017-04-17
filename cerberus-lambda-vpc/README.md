# Cerberus Lambda VPC

This module contains CloudFormation that creates a VPC in us-east-1 with 4 AZs
Each AZ has a private and public subnet
The private subnets connect to the public subnet with NATs and EIBs
This VPC allows you to deploy Lambdas to this VPC and have predictable IP addresses

## Deployment

This component is configured with the aws-sam-deployer-plugin

If you are ok with lambdas running in us-east-1, if not you will need to create a new CloudFormation template that creates the resources that [us-east-lambda-vpc.yaml](cerberus-lambda-vpc/us-east-lambda-vpc.yaml) does, but for your desired region. 
 
1. [Configure profiles](https://github.com/Nike-Inc/cerberus-serverless-components/blob/master/README.md#profiles)
2. Run the following gradle command `./gradlew cerberus-lambda-vpc:deploySam --stacktrace`