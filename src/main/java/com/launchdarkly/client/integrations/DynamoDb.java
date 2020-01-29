package com.launchdarkly.client.integrations;

/**
 * Integration between the LaunchDarkly SDK and DynamoDB.
 * <p>
 * This API uses the persistent data store model that was introduced in version 4.12.0 of the LaunchDarkly Java SDK.
 * If you are using an older Java SDK version, use {@link com.launchdarkly.client.dynamodb.DynamoDbComponents}.
 *
 * @since 2.1.0
 */
public abstract class DynamoDb {
  /**
   * Returns a builder object for creating a DynamoDB-backed data store.
   * <p>
   * This object can be modified with {@link DynamoDbDataStoreBuilder} methods for any desired
   * custom DynamoDB options. Then, pass it to
   * {@link com.launchdarkly.client.Components#persistentDataStore(com.launchdarkly.client.interfaces.PersistentDataStoreFactory)}
   * and set any desired caching options. Finally, pass the result to
   * {@link com.launchdarkly.client.LDConfig.Builder#dataStore(com.launchdarkly.client.FeatureStoreFactory)}.
   * For example:
   * 
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataStore(
   *             Components.persistentDataStore(
   *                 DynamoDb.dataStore("my-table-name")
   *             ).cacheSeconds(15)
   *         )
   *         .build();
   * </code></pre>
   * 
   * Note that the specified table must already exist in DynamoDB. It must have a partition key
   * of "namespace", and a sort key of "key".
   * <p>
   * By default, the data store uses a basic DynamoDB client configuration that takes its
   * AWS credentials and region from AWS environment variables and/or local configuration files.
   * There are options in the builder for changing some configuration options, or you can
   * configure the DynamoDB client yourself and pass it to the builder with
   * {@link DynamoDbDataStoreBuilder#existingClient(software.amazon.awssdk.services.dynamodb.DynamoDbClient)}.
   * <p>
   * If you are using the same DynamoDB table as a feature store for multiple LaunchDarkly
   * environments, use the {@link DynamoDbDataStoreBuilder#prefix(String)} option and choose a 
   * different prefix string for each, so they will not interfere with each other's data.
   *  
   * @param tableName the table name in DynamoDB (must already exist)
   * @return a data store configuration object
   */
  public static DynamoDbDataStoreBuilder dataStore(String tableName) {
    return new DynamoDbDataStoreBuilder(tableName);
  }
}
