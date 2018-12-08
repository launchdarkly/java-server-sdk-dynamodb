package com.launchdarkly.client.dynamodb;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCaching;
import com.launchdarkly.client.FeatureStoreDatabaseTestBase;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.client.dynamodb.DynamoDBFeatureStoreCore.partitionKey;
import static com.launchdarkly.client.dynamodb.DynamoDBFeatureStoreCore.sortKey;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.paginators.ScanIterable;

/**
 * Runs the standard database feature store test suite that's defined in the Java SDK.
 * <p>
 * Note that you must be running a local DynamoDB instance on port 8000 to run these tests.
 * The simplest way to do this is:
 * <pre>
 *     docker run -p 8000:8000 amazon/dynamodb-local
 * </pre>
 */
public class DynamoDBFeatureStoreTest extends FeatureStoreDatabaseTestBase<FeatureStore> {

  private static final String TABLE_NAME = "LD_DYNAMODB_TEST_TABLE";
  private static final URI DYNAMODB_ENDPOINT = URI.create("http://localhost:8000");

  public DynamoDBFeatureStoreTest(boolean cached) {
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
    DynamoDbClient client = createTestClient();
    
    List<Map<String, AttributeValue>> itemsToDelete = new ArrayList<>();
    
    ScanIterable results = client.scanPaginator(builder -> builder.tableName(TABLE_NAME)
        .consistentRead(true)
        .projectionExpression("#namespace, #key")
        .expressionAttributeNames(ImmutableMap.of("#namespace", partitionKey, "#key", sortKey)));
    for (ScanResponse resp: results) {
      itemsToDelete.addAll(resp.items());
    }
    
    List<WriteRequest> requests = new ArrayList<>();
    for (Map<String, AttributeValue> item: itemsToDelete) {
      requests.add(WriteRequest.builder().deleteRequest(builder -> builder.key(item)).build());
    }
    
    DynamoDBFeatureStoreCore.batchWriteRequests(client, TABLE_NAME, requests);
  }
  
  @Override
  protected boolean setUpdateHook(FeatureStore storeUnderTest, final Runnable hook) {
    DynamoDBFeatureStoreCore core = (DynamoDBFeatureStoreCore)((CachingStoreWrapper)storeUnderTest).getCore();
    core.setUpdateHook(hook);
    return true;
  }
  
  private void createTableIfNecessary() {
    DynamoDbClient client = createTestClient();
    try {
      client.describeTable(DescribeTableRequest.builder().tableName(TABLE_NAME).build());
      return; // table exists
    } catch (ResourceNotFoundException e) {
      // fall through to code below - we'll create the table
    } catch (InternalServerErrorException e) {
      throw e;
    }
    
    CreateTableRequest request = CreateTableRequest.builder()
        .tableName(TABLE_NAME)
        .keySchema(
            KeySchemaElement.builder().attributeName(partitionKey).keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName(sortKey).keyType(KeyType.RANGE).build()
        ).attributeDefinitions(
            AttributeDefinition.builder().attributeName(partitionKey).attributeType(ScalarAttributeType.S).build(),
            AttributeDefinition.builder().attributeName(sortKey).attributeType(ScalarAttributeType.S).build()
        ).provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
        .build();
    
    client.createTable(request);
  }
  
  private DynamoDBFeatureStoreBuilder baseBuilder() {
    return new DynamoDBFeatureStoreBuilder(TABLE_NAME)
        .endpoint(DYNAMODB_ENDPOINT)
        .region(Region.US_EAST_1)
        .caching(cached ? FeatureStoreCaching.enabled().ttlSeconds(30) : FeatureStoreCaching.disabled())
        .credentials(getTestCredentials());
  }
  
  private DynamoDbClient createTestClient() {
    return DynamoDbClient.builder()
        .endpointOverride(DYNAMODB_ENDPOINT)
        .region(Region.US_EAST_1)
        .credentialsProvider(getTestCredentials())
        .build();
  }
  
  private AwsCredentialsProvider getTestCredentials() {
    // The values here don't matter, it just expects us to provide something (since there may not be AWS
    // environment variables or config files where the tests are running)
    return StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret"));
  }
}
