package com.launchdarkly.client.integrations;

import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCacheConfig;
import com.launchdarkly.client.FeatureStoreDatabaseTestBase;
import com.launchdarkly.client.dynamodb.DynamoDbComponents;
import com.launchdarkly.client.dynamodb.DynamoDbFeatureStoreBuilder;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import org.junit.BeforeClass;

import java.net.URI;

import software.amazon.awssdk.regions.Region;

/**
 * Runs the standard database feature store test suite that's defined in the Java SDK.
 * <p>
 * Note that you must be running a local DynamoDB instance on port 8000 to run these tests.
 * The simplest way to do this is:
 * <pre>
 *     docker run -p 8000:8000 amazon/dynamodb-local
 * </pre>
 */
@SuppressWarnings({ "deprecation", "javadoc" })
public class DeprecatedDynamoDbFeatureStoreTest extends FeatureStoreDatabaseTestBase<FeatureStore> {

  private static final String TABLE_NAME = "LD_DYNAMODB_TEST_TABLE";
  private static final URI DYNAMODB_ENDPOINT = URI.create("http://localhost:8000");

  @BeforeClass
  public static void setUpAll() {
    DynamoDbDataStoreImplTest.createTableIfNecessary();
  }
  
  public DeprecatedDynamoDbFeatureStoreTest(boolean cached) {
    super(cached);
  }
  
  @Override
  protected FeatureStore makeStore() {
    return baseBuilder().createFeatureStore();
  }
  
  @Override
  protected FeatureStore makeStoreWithPrefix(String prefix) {
    return baseBuilder().prefix(prefix).createFeatureStore();
  }
  
  @Override
  protected void clearAllData() {
    DynamoDbDataStoreImplTest.clearEverything();
  }
  
  @Override
  protected boolean setUpdateHook(FeatureStore storeUnderTest, final Runnable hook) {
    DynamoDbDataStoreImpl core = (DynamoDbDataStoreImpl)((CachingStoreWrapper)storeUnderTest).getCore();
    core.setUpdateHook(hook);
    return true;
  }
  
  private DynamoDbFeatureStoreBuilder baseBuilder() {
    return DynamoDbComponents.dynamoDbFeatureStore(TABLE_NAME)
        .endpoint(DYNAMODB_ENDPOINT)
        .region(Region.US_EAST_1)
        .caching(cached ? FeatureStoreCacheConfig.enabled().ttlSeconds(30) : FeatureStoreCacheConfig.disabled())
        .credentials(DynamoDbDataStoreImplTest.getTestCredentials());
  }
}
