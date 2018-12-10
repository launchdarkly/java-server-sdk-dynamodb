package com.launchdarkly.client.dynamodb;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCaching;
import com.launchdarkly.client.FeatureStoreFactory;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import java.net.URI;

/**
 * Builder/factory class for the DynamoDB feature store.
 * <p>
 * Create this builder by calling {@link DatabaseComponents#dynamoDbFeatureStore(String)}, then
 * optionally modify its properties with builder methods, and then include it in your client
 * configuration with {@link LDConfig.Builder#featureStoreFactory(FeatureStoreFactory)}.
 * <p>
 * The AWS SDK provides many configuration options for a DynamoDB client. This class has
 * corresponding methods for some of the most commonly used ones. If you need more sophisticated
 * control over the DynamoDB client, you can construct one of your own and pass it in with the
 * {@link #existingClient(AmazonDynamoDB)} method.
 */
public class DynamoDbFeatureStoreBuilder implements FeatureStoreFactory {
  private final String tableName;
  
  private String prefix;
  private AmazonDynamoDB existingClient;
  private AmazonDynamoDBClientBuilder clientBuilder;
  
  private FeatureStoreCaching caching = FeatureStoreCaching.DEFAULT;
  
  DynamoDbFeatureStoreBuilder(String tableName) {
    this.tableName = tableName;
    clientBuilder = AmazonDynamoDBClient.builder();
  }
  
  @Override
  public FeatureStore createFeatureStore() {  
    AmazonDynamoDB client = (existingClient != null) ? existingClient : clientBuilder.build();
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
  public DynamoDbFeatureStoreBuilder clientConfiguration(ClientConfiguration config) {
    clientBuilder.setClientConfiguration(config);
    return this;
  }
  
  /**
   * Sets the AWS client credentials. If you do not set them programmatically, the AWS SDK will
   * attempt to find them in environment variables and/or local configuration files.
   *
   * @param credentialsProvider a source of credentials
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder credentials(AWSCredentialsProvider credentialsProvider) {
    clientBuilder.setCredentials(credentialsProvider);
    return this;
  }
  
  /**
   * Sets the service endpoint and AWS region to use. Normally, you will not use this, as AWS
   * determines the service endpoint based on your region. However, you can set it explicitly if
   * you are running your own DynamoDB instance.
   * 
   * @param endpointUri the custom endpoint URI
   * @param region the AWS region name
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder endpointAndRegion(URI endpointUri, String region) {
    clientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUri.toString(), region));
    return this;
  }
  
  /**
   * Sets the AWS region to use. If you do not set this, AWS will attempt to determine it from
   * environment variables and/or local configuration files.
   * 
   * @param region the AWS region name
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder region(String region) {
    clientBuilder.setRegion(region);
    return this;
  }

  /**
   * Sets the AWS region to use. If you do not set this, AWS will attempt to determine it from
   * environment variables and/or local configuration files.
   * 
   * @param region the AWS region enum
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder region(Regions region) {
    clientBuilder.withRegion(region);
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
  public DynamoDbFeatureStoreBuilder existingClient(AmazonDynamoDB existingClient) {
    this.existingClient = existingClient;
    return this;
  }
  
  /**
   * Specifies whether local caching should be enabled and if so, sets the cache properties. Local
   * caching is enabled by default; see {@link FeatureStoreCaching#DEFAULT}. To disable it, pass
   * {@link FeatureStoreCaching#disabled()} to this method.
   * 
   * @param caching a {@link FeatureStoreCaching} object specifying caching parameters
   * @return the builder
   */
  public DynamoDbFeatureStoreBuilder caching(FeatureStoreCaching caching) {
    this.caching = caching;
    return this;
  }
}
