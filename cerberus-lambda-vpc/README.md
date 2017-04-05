# Cerberus Lambda VPC

This module contains cloudformation that creates a VPC in US East 1 with 4 AZs
Each AZ has a private and public subnet
The private subnets connect to the public subnet with NATs and EIBs
This VPC allows you to deploy Lambdas to this VPC and have predictable IP addresses

For you convenience if you are ok with lambdas running in us-east-1 you can simply run 
`./gradlew cerberus-lambda-vpc:deploySam --stacktrace` after configuring a global profile./gradlew cerberus-lambda-vpc:deploySam