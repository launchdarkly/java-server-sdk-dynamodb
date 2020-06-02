# Change log

All notable changes to the LaunchDarkly Java SDK DynamoDB integration will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [3.0.0] - 2020-06-02
This release is for use with versions 5.0.0 and higher of [`launchdarkly-java-server-sdk`](https://github.com/launchdarkly/java-server-sdk).

For more information about changes in the SDK database integrations, see the [4.x to 5.0 migration guide](https://docs-stg.launchdarkly.com/252/sdk/server-side/java/migration-4-to-5/).

### Changed:
- The entry point is now `com.launchdarkly.sdk.server.integrations.DynamoDb` rather than `com.launchdarkly.client.integrations.DynamoDb`.
- The SLF4J logger name is now `com.launchdarkly.sdk.server.LDClient.DataStore.DynamoDb` rather than `com.launchdarkly.client.integrations.DynamoDbDataStoreImpl`.

### Removed:
- Removed the deprecated entry point `com.launchdarkly.client.dynamodb.DynamoDbComponents`.


## [2.1.0] - 2020-01-30
### Added:
- New classes `com.launchdarkly.client.integrations.DynamoDb` and `com.launchdarkly.client.integrations.DynamoDbDataStoreBuilder`, which serve the same purpose as the previous classes but are designed to work with the newer persistent data store API introduced in [Java SDK 4.12.0](https://github.com/launchdarkly/java-server-sdk/releases/tag/4.12.0).

### Deprecated:
- The old interface in the `com.launchdarkly.client.integrations.dynamodb` package.

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
