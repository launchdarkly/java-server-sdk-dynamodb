package com.launchdarkly.client.dynamodb;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCacheConfig;
import com.launchdarkly.client.FeatureStoreFactory;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.integrations.DynamoDbDataStoreBuilder;
import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.value.LDValue;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Deprecated builder class for the Redis-based persistent data store.
 * <p>
 * The replacement for this class is {@link com.launchdarkly.client.integrations.DynamoDb}.
 * This class is retained for backward compatibility with older Java SDK versions and will be removed in a
 * future version. 
 * 
 * @deprecated Use {@link com.launchdarkly.client.integrations.DynamoDb#dataStore(String)}
 */
@Deprecated
public class DynamoDbFeatureStoreBuilder implements FeatureStoreFactory, DiagnosticDescription {
  private final PersistentDataStoreBuilder wrappedOuterBuilder;
  private final DynamoDbDataStoreBuilder wrappedBuilder;
  
  DynamoDbFeatureStoreBuilder(String tableName) {
    wrappedBuilder = com.launchdarkly.client.integrations.DynamoDb.dataStore(tableName);
    wrappedOuterBuilder = Components.persistentDataStore(wrappedBuilder);
  }
  
  @Override
  public FeatureStore createFeatureStore() {
    return wrappedOuterBuilder.createFeatureStore();
  }
  
  /**
   * Sets the main AWS client configuration options for the DynamoDB client.
   * 
   * @param config an AWS client configuration object
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder clientOverrideConfiguration(ClientOverrideConfiguration config) {
    wrappedBuilder.clientOverrideConfiguration(config);
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
    wrappedBuilder.credentials(credentialsProvider);
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
    wrappedBuilder.endpoint(endpointUri);
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
    wrappedBuilder.region(region);
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
    wrappedBuilder.prefix(prefix);
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
    wrappedBuilder.existingClient(existingClient);
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
    wrappedOuterBuilder.cacheTime(caching.getCacheTime(), caching.getCacheTimeUnit());
    wrappedOuterBuilder.staleValuesPolicy(caching.getStaleValuesPolicy().toNewEnum());
    return this;
  }

  @Override
  public LDValue describeConfiguration(LDConfig config) {
    return LDValue.of("DynamoDB");
  }
}
