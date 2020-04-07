# LaunchDarkly SDK for Java - DynamoDB integration

[![Circle CI](https://circleci.com/gh/launchdarkly/java-server-sdk-dynamodb.svg?style=shield)](https://circleci.com/gh/launchdarkly/java-server-sdk-dynamodb)
[![Javadocs](http://javadoc.io/badge/com.launchdarkly/launchdarkly-java-server-sdk-dynamodb-store.svg)](http://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk-dynamodb-store)

This library provides a DynamoDB-backed persistence mechanism (data store) for the [LaunchDarkly Java SDK](https://github.com/launchdarkly/java-server-sdk), replacing the default in-memory data store.

This version of the library is for use with LaunchDarkly Java SDK versions greater than or equal to 4.12.0 and less than 5.0; for Java SDK 5.0 and above, the minimum version of this library is 3.0. It requires at least version 2.1 of the AWS SDK for Java. The minimum Java version is 8 (because that is the minimum Java version of the AWS SDK 2.x). If you need to use Java 7, or if you are already using AWS SDK 1.x for some other purpose, you can use the 1.x releases of this library (which are developed on the "aws-v1" branch of the repository).

See the [API documentation](https://launchdarkly.github.io/java-server-sdk-dynamodb) for details on classes and methods.

For more information, see also: [Using a persistent data store](https://docs.launchdarkly.com/v2.0/docs/using-a-persistent-feature-store).

## Quick setup

This assumes that you have already installed the LaunchDarkly Java SDK.

1. In DynamoDB, create a table which has the following schema: a partition key called "namespace" and a sort key called "key", both with a string type. The LaunchDarkly library does not create the table automatically, because it has no way of knowing what additional properties (such as permissions and throughput) you would want it to have.

2. Add this library to your project:

        <dependency>
          <groupId>com.launchdarkly</groupId>
          <artifactId>launchdarkly-java-server-sdk-dynamodb-store</artifactId>
          <version>2.1.0</version>
        </dependency>

3. If you do not already have the AWS SDK in your project, add the DynamoDB part of it. (This needs to be added separately, rather than being included in the LaunchDarkly jar, because AWS classes are exposed in the public interface.)

        <dependency>
          <groupId>software.amazon.awssdk</groupId>
          <artifactId>dynamodb</artifactId>
          <version>2.1.4</version>
        </dependency>

4. Import the LaunchDarkly package and the package for this library:

        import com.launchdarkly.client.*;
        import com.launchdarkly.client.integrations.*;

5. When configuring your SDK client, add the DynamoDB feature store:

        DynamoDbDataStoreBuilder dynamoDbStore = DynamoDb.dataStore("my-table-name");
        
        LDConfig config = new LDConfig.Builder()
            .dataStore(Components.persistentDataStore(dynamoDbStore))
            .build();
        
        LDClient client = new LDClient("YOUR SDK KEY", config);

By default, the DynamoDB client will try to get your AWS credentials and region name from environment variables and/or local configuration files, as described in the AWS SDK documentation. There are methods in `DynamoDbDataStoreBuilder` for changing the configuration options. Alternatively, if you already have a fully configured DynamoDB client object, you can tell LaunchDarkly to use that:

        DynamoDbDataStoreBuilder dynamoDbStore = DynamoDb.dataStore("my-table-name")
            .existingClient(myDynamoDbClientInstance);

## Caching behavior

To reduce traffic to DynamoDB, there is an optional in-memory cache that retains the last known data for a configurable amount of time. This is on by default; to turn it off (and guarantee that the latest feature flag data will always be retrieved from DynamoDB for every flag evaluation), configure the store as follows:

By default, for any persistent data store, the Java SDK uses an in-memory cache to reduce traffic to the database; this retains the last known data for a configurable amount of time. To change the caching behavior or disable caching, use the `PersistentDataStoreBuilder` methods:

        LDConfig configWithLongerCacheTtl = new LDConfig.Builder()
            .dataStore(
                Components.persistentDataStore(dynamoDbStore).cacheSeconds(60)
            )
            .build();

        LDConfig configWithNoCaching = new LDConfig.Builder()
            .dataStore(
                Components.persistentDataStore(dynamoDbStore).noCaching()
            )
            .build();

For other ways to control the behavior of the cache, see `PersistentDataStoreBuilder` in the Java SDK.

## About LaunchDarkly
 
* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Check out [our documentation](https://docs.launchdarkly.com/docs) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [blog.launchdarkly.com](https://blog.launchdarkly.com/  "LaunchDarkly Blog Documentation") for the latest product updates
    * [Feature Flagging Guide](https://github.com/launchdarkly/featureflags/  "Feature Flagging Guide") for best practices and strategies
