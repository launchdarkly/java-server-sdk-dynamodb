# Change log

All notable changes to the LaunchDarkly Java SDK DynamoDB integration will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [2.0.2] - 2019-12-11
### Changed:
- Updated the AWS SDK DynamoDB dependency to version 2.10.32. Your application can always specify its own desired version of the dependency; this is just the version that is used for building the library.

## [2.0.1] - 2019-05-13
### Changed:
- Corresponding to the SDK package name change from `com.launchdarkly:launchdarkly-client` to `com.launchdarkly:launchdarkly-java-server-sdk`, this package is now called `com.launchdarkly:launchdarkly-java-server-sdk-dynamodb-store`. The functionality of the package, including the package names and class names in the code, has not changed.

## [1.0.1] - 2019-05-13
### Changed:
- Corresponding to the SDK package name change from `com.launchdarkly:launchdarkly-client` to `com.launchdarkly:launchdarkly-java-server-sdk`, this package is now called `com.launchdarkly:launchdarkly-java-server-sdk-dynamodb-store`. The functionality of the package, including the package names and class names in the code, has not changed.

## [2.0.0] - 2018-12-12

Initial release of the implementation that is based on AWS SDK 2.x. This has the same functionality as version 1.0.0, but uses the DynamoDB client classes from the more recent AWS API.

Note that AWS SDK 2.x has a minimum Java version of 8, so if you require Java 7 compatibility you should use the 1.x releases of this library.

## [1.0.0] - 2018-12-12

Initial release of the implementation that is based on AWS SDK 1.x.
