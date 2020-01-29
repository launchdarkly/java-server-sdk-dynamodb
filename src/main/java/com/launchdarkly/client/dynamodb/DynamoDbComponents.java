package com.launchdarkly.client.dynamodb;

/**
 * Deprecated entry point for the DynamoDB data store. This is for use with the older feature store API
 * in Java SDK 4.11.x and below. For Java SDK 4.12 and above, use {@link com.launchdarkly.client.integrations.DynamoDb}.
 * 
 * @deprecated Use {@link com.launchdarkly.client.integrations.DynamoDb}.
 */
@Deprecated
public abstract class DynamoDbComponents {
  /**
   * Creates a builder for a DynamoDB feature store. You can modify any of the store's properties with
   * {@link DynamoDbFeatureStoreBuilder} methods before adding it to your client configuration with
   * {@link com.launchdarkly.client.LDConfig.Builder#featureStoreFactory(com.launchdarkly.client.FeatureStoreFactory)}.
   * 
   * @param tableName The table name in DynamoDB. This table must already exist (see package
   * documentation).
   * @return the builder
   * @deprecated Use {@link com.launchdarkly.client.integrations.DynamoDb#dataStore(String)}
   */
  @Deprecated
  public static DynamoDbFeatureStoreBuilder dynamoDbFeatureStore(String tableName) {
    return new DynamoDbFeatureStoreBuilder(tableName);
  }
}
