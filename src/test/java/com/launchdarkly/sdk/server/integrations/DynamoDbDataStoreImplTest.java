package com.launchdarkly.sdk.server.integrations;

import static com.launchdarkly.sdk.server.integrations.TestUtils.baseBuilder;
import static com.launchdarkly.sdk.server.integrations.TestUtils.clearEverything;
import static com.launchdarkly.sdk.server.integrations.TestUtils.createTableIfNecessary;

import org.junit.BeforeClass;

/**
 * Runs the standard database data store test suite that's defined in the Java SDK.
 * <p>
 * Note that you must be running a local DynamoDB instance on port 8000 to run these tests.
 * The simplest way to do this is:
 * <pre>
 *     docker run -p 8000:8000 amazon/dynamodb-local
 * </pre>
 */
@SuppressWarnings("javadoc")
public class DynamoDbDataStoreImplTest extends PersistentDataStoreTestBase<DynamoDbDataStoreImpl> {

  @BeforeClass
  public static void setUpAll() {
    createTableIfNecessary();
  }
  
  @Override
  protected DynamoDbDataStoreImpl makeStore() {
    return (DynamoDbDataStoreImpl)baseBuilder().createPersistentDataStore(null);
  }
  
  @Override
  protected DynamoDbDataStoreImpl makeStoreWithPrefix(String prefix) {
    return (DynamoDbDataStoreImpl)baseBuilder().prefix(prefix).createPersistentDataStore(null);
  }
  
  @Override
  protected void clearAllData() {
    clearEverything(null);
  }
  
  @Override
  protected boolean setUpdateHook(DynamoDbDataStoreImpl storeUnderTest, final Runnable hook) {
    storeUnderTest.setUpdateHook(hook);
    return true;
  }
}
