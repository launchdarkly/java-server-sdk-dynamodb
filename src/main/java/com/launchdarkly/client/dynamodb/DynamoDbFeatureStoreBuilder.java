package com.launchdarkly.client.dynamodb;

import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCacheConfig;
import com.launchdarkly.client.FeatureStoreFactory;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * Builder/factory class for the DynamoDB feature store.
 * <p>
 * Create this builder by calling {@link DynamoDbComponents#dynamoDbFeatureStore(String)}, then
 * optionally modify its properties with builder methods, and then include it in your client
 * configuration with {@link LDConfig.Builder#featureStoreFactory(FeatureStoreFactory)}.
 * <p>
 * The AWS SDK provides many configuration options for a DynamoDB client. This class has
 * corresponding methods for some of the most commonly used ones. If you need more sophisticated
 * control over the DynamoDB client, you can construct one of your own and pass it in with the
 * {@link #existingClient(DynamoDbClient)} method.
 */
public class DynamoDbFeatureStoreBuilder implements FeatureStoreFactory {
  private final String tableName;
  
  private String prefix;
  private DynamoDbClient existingClient;
  private DynamoDbClientBuilder clientBuilder;
  
  private FeatureStoreCacheConfig caching = FeatureStoreCacheConfig.DEFAULT;
  
  DynamoDbFeatureStoreBuilder(String tableName) {
    this.tableName = tableName;
    clientBuilder = DynamoDbClient.builder();
  }
  
  @Override
  public FeatureStore createFeatureStore() {  
    DynamoDbClient client = (existingClient != null) ? existingClient : clientBuilder.build();
    DynamoDbFeatureStoreCore core = new DynamoDbFeatureStoreCore(client, tableName, prefix);
    CachingStoreWrapper wrapper = new CachingStoreWrapper.Builder(core).caching(caching).build();
    return wrapper;
  }
  
  /**
   * Sets the main AWS client configuration options for the DynamoDB client.
   * 
   * @param config an AWS client configuration object
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder clientOverrideConfiguration(ClientOverrideConfiguration config) {
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
  public DynamoDbFeatureStoreBuilder credentials(AwsCredentialsProvider credentialsProvider) {
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
  public DynamoDbFeatureStoreBuilder endpoint(URI endpointUri) {
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
  public DynamoDbFeatureStoreBuilder region(Region region) {
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
  public DynamoDbFeatureStoreBuilder prefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  /**
   * Specifies an existing, already-configured DynamoDB client instance that the feature store
   * should use rather than creating one of its own. If you specify an existing client, then the
   * other builder methods for configuring DynamoDB are ignored.
   *  
   * @param existingClient an existing DynamoDB client instance
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder existingClient(DynamoDbClient existingClient) {
    this.existingClient = existingClient;
    return this;
  }
  
  /**
   * Specifies whether local caching should be enabled and if so, sets the cache properties. Local
   * caching is enabled by default; see {@link FeatureStoreCacheConfig#DEFAULT}. To disable it, pass
   * {@link FeatureStoreCacheConfig#disabled()} to this method.
   * 
   * @param caching a {@link FeatureStoreCacheConfig} object specifying caching parameters
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder caching(FeatureStoreCacheConfig caching) {
    this.caching = caching;
    return this;
  }
}
