package com.launchdarkly.client.dynamodb;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;
import com.launchdarkly.client.utils.FeatureStoreCore;
import com.launchdarkly.client.utils.FeatureStoreHelpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.model.Condition;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.paginators.QueryIterable;

/**
 * Internal implementation of the DynamoDB feature store.
 * <p>
 * Implementation notes:
 * <ul>
 * 
 * <li> Feature flags, segments, and any other kind of entity the LaunchDarkly client may wish
 * to store, are all put in the same table. The only two required attributes are "key" (which
 * is present in all storeable entities) and "namespace" (a parameter from the client that is
 * used to disambiguate between flags and segments).
 *
 * <li> Because of DynamoDB's restrictions on attribute values (e.g. empty strings are not
 * allowed), the standard DynamoDB marshaling mechanism with one attribute per object property
 * is not used. Instead, the entire object is serialized to JSON and stored in a single
 * attribute, "item". The "version" property is also stored as a separate attribute since it
 * is used for updates.
 *
 * <li> Since DynamoDB doesn't have transactions, the init() method - which replaces the entire data
 * store - is not atomic, so there can be a race condition if another process is adding new data
 * via upsert(). To minimize this, we don't delete all the data at the start; instead, we update
 * the items we've received, and then delete all other items. That could potentially result in
 * deleting new data from another process, but that would be the case anyway if the init()
 * happened to execute later than the upsert(); we are relying on the fact that normally the
 * process that did the init() will also receive the new data shortly and do its own upsert().
 *
 * <li> DynamoDB has a maximum item size of 400KB. Since each feature flag or user segment is
 * stored as a single item, this mechanism will not work for extremely large flags or segments.
 * </ul>
 */
class DynamoDbFeatureStoreCore implements FeatureStoreCore {
  private static final Logger logger = LoggerFactory.getLogger(DynamoDbFeatureStoreCore.class);
  
  static final String partitionKey = "namespace";
  static final String sortKey = "key";
  private static final String versionAttribute = "version";
  private static final String itemJsonAttribute = "item";
  
  private final DynamoDbClient client;
  private final String tableName;
  private final String prefix;
  
  private Runnable updateHook;
  
  DynamoDbFeatureStoreCore(DynamoDbClient client, String tableName, String prefix) {
    this.client = client;
    this.tableName = tableName;
    this.prefix = "".equals(prefix) ? null : prefix;
  }
  
  @Override
  public void close() throws IOException {
    client.close();
  }

  @Override
  public VersionedData getInternal(VersionedDataKind<?> kind, String key) {
    GetItemResponse resp = getItemByKeys(namespaceForKind(kind), key);
    return unmarshalItem(kind, resp.item());
  }

  @Override
  public Map<String, VersionedData> getAllInternal(VersionedDataKind<?> kind) {
    Map<String, VersionedData> itemsOut = new HashMap<>();
    for (QueryResponse resp: client.queryPaginator(makeQueryForKind(kind).build())) {
      for (Map<String, AttributeValue> item: resp.items()) {
        VersionedData itemOut = unmarshalItem(kind, item);
        if (itemOut != null) {
          itemsOut.put(itemOut.getKey(), itemOut);
        }
      }
    }
    return itemsOut;
  }

  @Override
  public void initInternal(Map<VersionedDataKind<?>, Map<String, VersionedData>> allData) {
    // Start by reading the existing keys; we will later delete any of these that weren't in allData.
    Set<Map.Entry<String, String>> unusedOldKeys = readExistingKeys(allData.keySet());
    
    List<WriteRequest> requests = new ArrayList<>();
    int numItems = 0;
    
    // Insert or update every provided item
    for (Map.Entry<VersionedDataKind<?>, Map<String, VersionedData>> entry: allData.entrySet()) {
      VersionedDataKind<?> kind = entry.getKey();
      for (VersionedData item: entry.getValue().values()) {       
        Map<String, AttributeValue> encodedItem = marshalItem(kind, item);
        requests.add(WriteRequest.builder().putRequest(builder -> builder.item(encodedItem)).build());
        
        Map.Entry<String, String> combinedKey = new AbstractMap.SimpleEntry<>(
            namespaceForKind(kind), item.getKey());
        unusedOldKeys.remove(combinedKey);
        
        numItems++;
      }
    }
    
    // Now delete any previously existing items whose keys were not in the current data
    for (Map.Entry<String, String> combinedKey: unusedOldKeys) {
      if (!combinedKey.getKey().equals(initedKey())) {
        Map<String, AttributeValue> keys = ImmutableMap.of(
            partitionKey, AttributeValue.builder().s(combinedKey.getKey()).build(),
            sortKey, AttributeValue.builder().s(combinedKey.getValue()).build());
        requests.add(WriteRequest.builder().deleteRequest(builder -> builder.key(keys)).build());
      }
    }
    
    // Now set the special key that we check in initializedInternal()
    Map<String, AttributeValue> initedItem = ImmutableMap.of(
        partitionKey, AttributeValue.builder().s(initedKey()).build(),
        sortKey, AttributeValue.builder().s(initedKey()).build());
    requests.add(WriteRequest.builder().putRequest(builder -> builder.item(initedItem)).build());
    
    batchWriteRequests(client, tableName, requests);
    
    logger.info("Initialized table {} with {} items", tableName, numItems);
  }

  @Override
  public VersionedData upsertInternal(VersionedDataKind<?> kind, VersionedData item) {
    Map<String, AttributeValue> encodedItem = marshalItem(kind, item);
    
    if (updateHook != null) { // instrumentation for tests
      updateHook.run();
    }
    
    try {
      client.putItem(builder -> builder.tableName(tableName)
          .item(encodedItem)
          .conditionExpression("attribute_not_exists(#namespace) or attribute_not_exists(#key) or :version > #version")
          .expressionAttributeNames(ImmutableMap.of(
              "#namespace", partitionKey,
              "#key", sortKey,
              "#version", versionAttribute))
          .expressionAttributeValues(ImmutableMap.of(
              ":version", AttributeValue.builder().n(String.valueOf(item.getVersion())).build()))
      );
    } catch (ConditionalCheckFailedException e) {
      // The item was not updated because there's a newer item in the database.
      // We must now read the item that's in the database and return it, so CachingStoreWrapper can cache it.
      return getInternal(kind, item.getKey());
    }
    
    return item;
  }

  @Override
  public boolean initializedInternal() {
    GetItemResponse resp = getItemByKeys(initedKey(), initedKey());
    return resp.item() != null && resp.item().size() > 0;
  }
  
  public void setUpdateHook(Runnable updateHook) {
    this.updateHook = updateHook;
  }
  
  private String prefixedNamespace(String base) {
    return prefix == null ? base : (prefix + ":" + base);
  }
  
  private String namespaceForKind(VersionedDataKind<?> kind) {
    return prefixedNamespace(kind.getNamespace());
  }
  
  private String initedKey() {
    return prefixedNamespace("$inited");
  }

  private QueryRequest.Builder makeQueryForKind(VersionedDataKind<?> kind) {
    Map<String, Condition> keyConditions = ImmutableMap.of(
        partitionKey,
        Condition.builder()
          .comparisonOperator(ComparisonOperator.EQ)
          .attributeValueList(AttributeValue.builder().s(namespaceForKind(kind)).build())
          .build()
    );
    return QueryRequest.builder()
        .tableName(tableName)
        .consistentRead(true)
        .keyConditions(keyConditions);
  }
  
  private GetItemResponse getItemByKeys(String namespace, String key) {
    Map<String, AttributeValue> keyMap = ImmutableMap.of(
        partitionKey, AttributeValue.builder().s(namespace).build(),
        sortKey, AttributeValue.builder().s(key).build()
    );
    return client.getItem(builder -> builder.tableName(tableName)
        .consistentRead(true)
        .key(keyMap)
    );
  }
  
  private Set<Map.Entry<String, String>> readExistingKeys(Iterable<VersionedDataKind<?>> kinds) {
    Set<Map.Entry<String, String>> keys = new HashSet<>();
    for (VersionedDataKind<?> kind: kinds) {
      QueryRequest req = makeQueryForKind(kind)
        .projectionExpression("#namespace, #key")
        .expressionAttributeNames(ImmutableMap.of(
            "#namespace", partitionKey, "#key", sortKey))
        .build();
      QueryIterable queryResults = client.queryPaginator(req);
      for (QueryResponse resp: queryResults) {
        for (Map<String, AttributeValue> item: resp.items()) {
          String namespace = item.get(partitionKey).s();
          String key = item.get(sortKey).s();
          keys.add(new AbstractMap.SimpleEntry<>(namespace, key));
        }
      }
    }
    return keys;
  }
  
  private Map<String, AttributeValue> marshalItem(VersionedDataKind<?> kind, VersionedData item) {
    String json = FeatureStoreHelpers.marshalJson(item);
    return ImmutableMap.of(
        partitionKey, AttributeValue.builder().s(namespaceForKind(kind)).build(),
        sortKey, AttributeValue.builder().s(item.getKey()).build(),
        versionAttribute, AttributeValue.builder().n(String.valueOf(item.getVersion())).build(),
        itemJsonAttribute, AttributeValue.builder().s(json).build()
    );
  }
  
  private VersionedData unmarshalItem(VersionedDataKind<?> kind, Map<String, AttributeValue> item) {
    if (item == null || item.size() == 0) {
      return null;
    }
    AttributeValue jsonAttr = item.get(itemJsonAttribute);
    if (jsonAttr == null || jsonAttr.s() == null) {
      throw new IllegalStateException("DynamoDB map did not contain expected item string");
    }
    return FeatureStoreHelpers.unmarshalJson(kind, jsonAttr.s());
  }
  
  static void batchWriteRequests(DynamoDbClient client, String tableName, List<WriteRequest> requests) {
    int batchSize = 25;
    for (int i = 0; i < requests.size(); i += batchSize) {
      int limit = (i + batchSize < requests.size()) ? (i + batchSize) : requests.size();
      List<WriteRequest> batch = requests.subList(i, limit); 
      Map<String, List<WriteRequest>> batchMap = ImmutableMap.of(tableName, batch);
      client.batchWriteItem(builder -> builder.requestItems(batchMap));
    }
  }
}
