/**
 * This package provides a DynamoDB-backed feature store for the LaunchDarkly Java SDK.
 * <p>
 * For more details about how and why you can use a persistent feature store, see:
 * https://docs.launchdarkly.com/v2.0/docs/using-a-persistent-feature-store
 * <p>
 * To use the DynamoDB feature store with the LaunchDarkly client, you will first obtain a
 * builder by calling {@link com.launchdarkly.client.dynamodb.DynamoDbComponents#dynamoDbFeatureStore(String)}, then optionally
 * modify its properties, and then include it in your client configuration. For example:
 * 
 * <pre>
 * import com.launchdarkly.client.*;
 * import com.launchdarkly.client.dynamodb.*;

 * DynamoDbFeatureStoreBuilder store = DatabaseComponents.dynamoDbFeatureStore("my-table-name")
 *     .caching(FeatureStoreCacheConfig.enabled().ttlSeconds(30));
 * LDConfig config = new LDConfig.Builder()
 *     .featureStoreFactory(store)
 *     .build();
 * </pre>
 * 
 * Note that the specified table must already exist in DynamoDB. It must have a partition key
 * of "namespace", and a sort key of "key".
 * <p>
 * By default, the feature store uses a basic DynamoDB client configuration that takes its
 * AWS credentials and region from AWS environment variables and/or local configuration files.
 * There are options in the builder for changing some configuration options, or you can
 * configure the DynamoDB client yourself and pass it to the builder with
 * {@link com.launchdarkly.client.dynamodb.DynamoDbFeatureStoreBuilder#existingClient(software.amazon.awssdk.services.dynamodb.DynamoDbClient)}.
 * <p>
 * If you are using the same DynamoDB table as a feature store for multiple LaunchDarkly
 * environments, use the {@link com.launchdarkly.client.dynamodb.DynamoDbFeatureStoreBuilder#prefix(String)}
 * option and choose a different prefix string for each, so they will not interfere with each
 * other's data. 
 */
package com.launchdarkly.client.dynamodb;