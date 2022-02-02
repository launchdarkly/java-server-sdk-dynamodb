package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStore;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreFactory;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * Builder/factory class for the DynamoDB data store.
 * <p>
 * This can be used either for the main data store that holds feature flag data, or for the Big
 * Segment store, or both. If you are using both, they do not have to have the same parameters. For
 * instance, in this example the main data store uses a table called "table1" and the Big Segment
 * store uses a table called "table2":
 *
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataStore(
 *             Components.persistentDataStore(
 *                 DynamoDb.dataStore("table1")
 *             )
 *         )
 *         .bigSegments(
 *             Components.bigSegments(
 *                 DynamoDb.dataStore("table2")
 *             )
 *         )
 *         .build();
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
 * The AWS SDK provides many configuration options for a DynamoDB client. This class has
 * corresponding methods for some of the most commonly used ones. If you need more sophisticated
 * control over the DynamoDB client, you can construct one of your own and pass it in with the
 * {@link #existingClient(DynamoDbClient)} method.
 * 
 * @since 2.1.0
 */
public final class DynamoDbDataStoreBuilder implements PersistentDataStoreFactory, BigSegmentStoreFactory, DiagnosticDescription {
  private final String tableName;
  
  private String prefix;
  private DynamoDbClient existingClient;
  private DynamoDbClientBuilder clientBuilder;
  
  DynamoDbDataStoreBuilder(String tableName) {
    this.tableName = tableName;
    clientBuilder = DynamoDbClient.builder();
  }
  
  /**
   * Sets the main AWS client configuration options for the DynamoDB client.
   * 
   * @param config an AWS client configuration object
   * @return the builder
   */
  public DynamoDbDataStoreBuilder clientOverrideConfiguration(ClientOverrideConfiguration config) {
    clientBuilder.overrideConfiguration(config);
    return this;
  }
  
  /**
   * Sets the AWS client credentials. If you do not set them programmatically, the AWS SDK will
   * attempt to find them in environment variables and/or local configuration files.
   *
   * @param credentialsProvider a source of credentials
   * @return the builder
   */
  public DynamoDbDataStoreBuilder credentials(AwsCredentialsProvider credentialsProvider) {
    clientBuilder.credentialsProvider(credentialsProvider);
    return this;
  }
  
  /**
   * Sets the service endpoint to use. Normally, you will not use this, as AWS determines the
   * service endpoint based on your region. However, you can set it explicitly if you are
   * running your own DynamoDB instance.
   * 
   * @param endpointUri the custom endpoint URI
   * @return the builder
   */
  public DynamoDbDataStoreBuilder endpoint(URI endpointUri) {
    clientBuilder.endpointOverride(endpointUri);
    return this;
  }
  
  /**
   * Sets the AWS region to use. If you do not set this, AWS will attempt to determine it from
   * environment variables and/or local configuration files.
   * 
   * @param region the AWS region
   * @return the builder
   */
  public DynamoDbDataStoreBuilder region(Region region) {
    clientBuilder.region(region);
    return this;
  }

  /**
   * Sets an optional namespace prefix for all keys stored in DynamoDB. Use this if you are sharing
   * the same database table between multiple clients that are for different LaunchDarkly
   * environments, to avoid key collisions. 
   *
   * @param prefix the namespace prefix
   * @return the builder
   */
  public DynamoDbDataStoreBuilder prefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  /**
   * Specifies an existing, already-configured DynamoDB client instance that the data store
   * should use rather than creating one of its own. If you specify an existing client, then the
   * other builder methods for configuring DynamoDB are ignored.
   *  
   * @param existingClient an existing DynamoDB client instance
   * @return the builder
   */
  public DynamoDbDataStoreBuilder existingClient(DynamoDbClient existingClient) {
    this.existingClient = existingClient;
    return this;
  }

  /**
   * Called internally by the SDK to create the actual data store instance.
   * @return the data store configured by this builder
   */
  @Override
  public PersistentDataStore createPersistentDataStore(ClientContext context) {  
    DynamoDbClient client = (existingClient != null) ? existingClient : clientBuilder.build();
    return new DynamoDbDataStoreImpl(client, existingClient != null, tableName, prefix);
  }

  /**
   * Called internally by the SDK to create the actual Big Segment store instance.
   * @return the Big Segment store configured by this builder
   */
  @Override
  public BigSegmentStore createBigSegmentStore(ClientContext context) {
    DynamoDbClient client = (existingClient != null) ? existingClient : clientBuilder.build();
    return new DynamoDbBigSegmentStoreImpl(client, existingClient != null, tableName, prefix);
  }

  @Override
  public LDValue describeConfiguration(BasicConfiguration config) {
    return LDValue.of("DynamoDB");
  }
}
