package com.launchdarkly.client.dynamodb;

import com.launchdarkly.client.LDConfig;

/**
 * Entry point for using the DynamoDB feature store.
 */
public abstract class DynamoDbComponents {
  /**
   * Creates a builder for a DynamoDB feature store. You can modify any of the store's properties with
   * {@link DynamoDbFeatureStoreBuilder} methods before adding it to your client configuration with
   * {@link LDConfig.Builder#featureStoreFactory(com.launchdarkly.client.FeatureStoreFactory)}.
   * 
   * @param tableName The table name in DynamoDB. This table must already exist (see package
   * documentation).
   * @return the builder
   */
  public static DynamoDbFeatureStoreBuilder dynamoDbFeatureStore(String tableName) {
    return new DynamoDbFeatureStoreBuilder(tableName);
  }
}
