# Cerberus Serverless Components

[![][travis img]][travis]
[![Coverage Status](https://coveralls.io/repos/github/Nike-Inc/cerberus-serverless-components/badge.svg)](https://coveralls.io/github/Nike-Inc/cerberus-serverless-components)
[![][license img]][license]

This project contains the serverless components that can be used with Cerberus.

## Serverless Components

* [Cerberus CloudFront Lambda](cerberus-cloudfront-lambda/README.md) - Serverless function for processing CloudFront log events, to enable things such as rate limiting and optionally Google Analytics KPI tracking
* [Cerberus Cross Region Backup Lambda](cerberus-cross-region-backup-lambda/README.md) - Serverless function for for making complete backups of the data in Cerberus (CMS / Vault) and backing it up in a different region encrypted with KMS. With this function enabled, Cerberus operators can use the CLI to complete data restores in new regions or the original region.
* [Cerberus Lambda VPC](cerberus-lambda-vpc/README.md) - Cloudformation to create a VPC with EIBs and NATs so that Cerberus operators can run lambdas with predictable IP addresses. The backup lambda should run in this VPC so that the IPs can be white listed to avoid rate limiting.

## License

This project is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

[travis]:https://travis-ci.org/Nike-Inc/cerberus-serverless-components
[travis img]:https://api.travis-ci.org/Nike-Inc/cerberus-serverless-components.svg?branch=master

[license]:LICENSE.txt
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg
