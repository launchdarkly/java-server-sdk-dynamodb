package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreFactory;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory;

/**
 * Integration between the LaunchDarkly SDK and DynamoDB.
 *
 * @since 2.1.0
 */
public abstract class DynamoDb {
  /**
   * Returns a builder object for creating a DynamoDB-backed data store.
   * <p>
   * This can be used either for the main data store that holds feature flag data, or for the Big
   * Segment store, or both. If you are using both, they do not have to have the same parameters.
   * For instance, in this example the main data store uses a table called "table1" and the Big
   * Segment store uses a table called "table2":
   *
   * <pre><code>
   *   LDConfig config = new LDConfig.Builder()
   *       .dataStore(
   *           Components.persistentDataStore(
   *               DynamoDb.dataStore("table1")
   *           )
   *       )
   *       .bigSegments(
   *           Components.bigSegments(
   *               DynamoDb.dataStore("table2")
   *           )
   *       )
   *       .build();
   * </code></pre>
   *
   * Note that the builder is passed to one of two methods,
   * {@link com.launchdarkly.sdk.server.Components#persistentDataStore(PersistentDataStoreFactory)} or
   * {@link com.launchdarkly.sdk.server.Components#bigSegments(BigSegmentStoreFactory)}, depending on
   * the context in which it is being used. This is because each of those contexts has its own
   * additional configuration options that are unrelated to the DynamoDb options. For instance, the
   * {@link com.launchdarkly.sdk.server.Components#persistentDataStore(PersistentDataStoreFactory)}
   * builder has options for caching:
   *
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataStore(
   *             Components.persistentDataStore(
   *                 DynamoDb.dataStore("table1")
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
   * If you are using the same DynamoDB table as a data store for multiple LaunchDarkly
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
