package com.launchdarkly.client.dynamodb;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.InternalServerErrorException;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.collect.ImmutableList;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCacheConfig;
import com.launchdarkly.client.FeatureStoreDatabaseTestBase;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.client.dynamodb.DynamoDbFeatureStoreCore.partitionKey;
import static com.launchdarkly.client.dynamodb.DynamoDbFeatureStoreCore.sortKey;

/**
 * Runs the standard database feature store test suite that's defined in the Java SDK.
 * <p>
 * Note that you must be running a local DynamoDB instance on port 8000 to run these tests.
 * The simplest way to do this is:
 * <pre>
 *     docker run -p 8000:8000 amazon/dynamodb-local
 * </pre>
 */
public class DynamoDbFeatureStoreTest extends FeatureStoreDatabaseTestBase<FeatureStore> {
  private static final String TABLE_NAME = "LD_DYNAMODB_TEST_TABLE";
  private static final URI DYNAMODB_ENDPOINT = URI.create("http://localhost:8000");
  
  public DynamoDbFeatureStoreTest(boolean cached) {
    super(cached);
    
    createTableIfNecessary();
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
    AmazonDynamoDB client = createTestClient();
    
    List<Map<String, AttributeValue>> itemsToDelete = new ArrayList<>();
    
    ScanRequest request = new ScanRequest(TABLE_NAME);
    request.setConsistentRead(true);
    request.setProjectionExpression("#namespace, #key");
    request.addExpressionAttributeNamesEntry("#namespace", partitionKey);
    request.addExpressionAttributeNamesEntry("#key", sortKey);
    boolean done = false;
    
    while (!done) {
      ScanResult result = client.scan(request);
      itemsToDelete.addAll(result.getItems());
      if (result.getLastEvaluatedKey() == null) {
        done = true;
      } else {
        request.setExclusiveStartKey(result.getLastEvaluatedKey());
      }
    }
    
    List<WriteRequest> requests = new ArrayList<>();
    for (Map<String, AttributeValue> item: itemsToDelete) {
      requests.add(new WriteRequest(new DeleteRequest(item)));
    }
    
    DynamoDbFeatureStoreCore.batchWriteRequests(client, TABLE_NAME, requests);
  }
  
  @Override
  protected boolean setUpdateHook(FeatureStore storeUnderTest, final Runnable hook) {
    DynamoDbFeatureStoreCore core = (DynamoDbFeatureStoreCore)((CachingStoreWrapper)storeUnderTest).getCore();
    core.setUpdateHook(hook);
    return true;
  }
  
  private void createTableIfNecessary() {
    AmazonDynamoDB client = createTestClient();
    try {
      client.describeTable(TABLE_NAME);
      return; // table exists
    } catch (ResourceNotFoundException e) {
      // fall through to code below - we'll create the table
    } catch (InternalServerErrorException e) {
      throw e;
    }
    
    List<KeySchemaElement> keys = ImmutableList.of(
        new KeySchemaElement(partitionKey, KeyType.HASH),
        new KeySchemaElement(sortKey, KeyType.RANGE));
    CreateTableRequest request = new CreateTableRequest(TABLE_NAME, keys);
    List<AttributeDefinition> attrDefs = ImmutableList.of(
        new AttributeDefinition(partitionKey, ScalarAttributeType.S),
        new AttributeDefinition(sortKey, ScalarAttributeType.S));
    request.setAttributeDefinitions(attrDefs);
    request.setProvisionedThroughput(new ProvisionedThroughput(1L, 1L));
    
    client.createTable(request);
  }
  
  private DynamoDbFeatureStoreBuilder baseBuilder() {
    return DynamoDbComponents.dynamoDbFeatureStore(TABLE_NAME)
        .endpointAndRegion(DYNAMODB_ENDPOINT, Regions.US_EAST_1.name())
        .caching(cached ? FeatureStoreCacheConfig.enabled().ttlSeconds(30) : FeatureStoreCacheConfig.disabled())
        .credentials(getTestCredentials());
  }
  
  private AmazonDynamoDB createTestClient() {
    return AmazonDynamoDBClient.builder()
        .withEndpointConfiguration(new EndpointConfiguration(DYNAMODB_ENDPOINT.toString(), Regions.US_EAST_1.name()))
        .withCredentials(getTestCredentials())
        .build();
  }
  
  private AWSCredentialsProvider getTestCredentials() {
    // The values here don't matter, it just expects us to provide something (since there may not be AWS
    // environment variables or config files where the tests are running)
    return new AWSStaticCredentialsProvider(new BasicAWSCredentials("key", "secret"));
  }
}
