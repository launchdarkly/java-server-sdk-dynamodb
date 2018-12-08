package com.launchdarkly.client.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.collect.ImmutableList;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
class DynamoDBFeatureStoreCore implements FeatureStoreCore {
  private static final Logger logger = LoggerFactory.getLogger(DynamoDBFeatureStoreCore.class);
  
  static final String partitionKey = "namespace";
  static final String sortKey = "key";
  private static final String versionAttribute = "version";
  private static final String itemJsonAttribute = "item";
  
  private final AmazonDynamoDB client;
  private final String tableName;
  private final String prefix;
  
  private Runnable updateHook;
  
  DynamoDBFeatureStoreCore(AmazonDynamoDB client, String tableName, String prefix) {
    this.client = client;
    this.tableName = tableName;
    this.prefix = "".equals(prefix) ? null : prefix;
  }
  
	@Override
	public void close() throws IOException {
	  client.shutdown();
	}

	@Override
	public <T extends VersionedData> T getInternal(VersionedDataKind<T> kind, String key) {
		GetItemResult result = getItemByKeys(namespaceForKind(kind), key);
		return unmarshalItem(kind, result.getItem());
	}

	@Override
	public <T extends VersionedData> Map<String, T> getAllInternal(VersionedDataKind<T> kind) {
	  Map<String, T> itemsOut = new HashMap<>();
    for (QueryResult result: paginateQuery(makeQueryForKind(kind))) {
      for (Map<String, AttributeValue> item: result.getItems()) {
        T itemOut = unmarshalItem(kind, item);
        if (itemOut != null) {
          itemsOut.put(itemOut.getKey(), itemOut);
        }
      }
    }
    return itemsOut;
	}

	@Override
	public void initInternal(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
	  // Start by reading the existing keys; we will later delete any of these that weren't in allData.
	  Set<Map.Entry<String, String>> unusedOldKeys = readExistingKeys(allData.keySet());
		
	  List<WriteRequest> requests = new ArrayList<>();
	  int numItems = 0;
	  
	  // Insert or update every provided item
	  for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> entry: allData.entrySet()) {
	    VersionedDataKind<?> kind = entry.getKey();
	    for (VersionedData item: entry.getValue().values()) {	      
	      Map<String, AttributeValue> encodedItem = marshalItem(kind, item);
	      requests.add(new WriteRequest(new PutRequest(encodedItem)));
	      
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
	          partitionKey, new AttributeValue(combinedKey.getKey()),
	          sortKey, new AttributeValue(combinedKey.getValue()));
	      requests.add(new WriteRequest(new DeleteRequest(keys)));
	    }
	  }
	  
	  // Now set the special key that we check in initializedInternal()
	  Map<String, AttributeValue> initedItem = ImmutableMap.of(
        partitionKey, new AttributeValue(initedKey()),
        sortKey, new AttributeValue(initedKey()));
	  requests.add(new WriteRequest(new PutRequest(initedItem)));
	  
	  batchWriteRequests(client, tableName, requests);
	  
	  logger.info("Initialized table {} with {} items", tableName, numItems);
	}

	@Override
	public <T extends VersionedData> T upsertInternal(VersionedDataKind<T> kind, T item) {
		Map<String, AttributeValue> encodedItem = marshalItem(kind, item);
		
		if (updateHook != null) { // instrumentation for tests
		  updateHook.run();
		}
		
		try {
		  PutItemRequest put = new PutItemRequest(tableName, encodedItem);
		  put.setConditionExpression("attribute_not_exists(#namespace) or attribute_not_exists(#key) or :version > #version");
		  put.addExpressionAttributeNamesEntry("#namespace", partitionKey);
		  put.addExpressionAttributeNamesEntry("#key", sortKey);
		  put.addExpressionAttributeNamesEntry("#version", versionAttribute);
		  put.addExpressionAttributeValuesEntry(":version", new AttributeValue().withN(String.valueOf(item.getVersion())));
		  client.putItem(put);
		} catch (ConditionalCheckFailedException e) {
		  // The item was not updated because there's a newer item in the database.
		  // We must now read the item that's in the database and return it, so CachingStoreWrapper can cache it.
		  return getInternal(kind, item.getKey());
		}
		
		return item;
	}

	@Override
	public boolean initializedInternal() {
    GetItemResult result = getItemByKeys(initedKey(), initedKey());
    return result.getItem() != null && result.getItem().size() > 0;
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
	
	private QueryRequest makeQueryForKind(VersionedDataKind<?> kind) {
    Condition cond = new Condition();
	  cond.setComparisonOperator(ComparisonOperator.EQ);
	  cond.setAttributeValueList(ImmutableList.of(new AttributeValue(namespaceForKind(kind))));

	  QueryRequest req = new QueryRequest(tableName);
	  req.setConsistentRead(true);
	  req.addKeyConditionsEntry(partitionKey, cond);
	  return req;
	}
	
	private GetItemResult getItemByKeys(String namespace, String key) {
	  Map<String, AttributeValue> keyMap = ImmutableMap.of(
        partitionKey, new AttributeValue(namespace),
        sortKey, new AttributeValue(key)
    );
	  GetItemRequest req = new GetItemRequest(tableName, keyMap, true);
	  return client.getItem(req);
	}
	
	private Set<Map.Entry<String, String>> readExistingKeys(Iterable<VersionedDataKind<?>> kinds) {
	  Set<Map.Entry<String, String>> keys = new HashSet<>();
	  for (VersionedDataKind<?> kind: kinds) {
	    QueryRequest req = makeQueryForKind(kind);
	    req.setProjectionExpression("#namespace, #key");
	    req.addExpressionAttributeNamesEntry("#namespace", partitionKey);
	    req.addExpressionAttributeNamesEntry("#key", sortKey);
	    for (QueryResult result: paginateQuery(req)) {
	      for (Map<String, AttributeValue> item: result.getItems()) {
	        String namespace = item.get(partitionKey).getS();
	        String key = item.get(sortKey).getS();
	        keys.add(new AbstractMap.SimpleEntry<>(namespace, key));
	      }
	    }
	  }
	  return keys;
	}
	
	private Map<String, AttributeValue> marshalItem(VersionedDataKind<?> kind, VersionedData item) {
	  String json = FeatureStoreHelpers.marshalJson(item);
	  return ImmutableMap.of(
        partitionKey, new AttributeValue(namespaceForKind(kind)),
        sortKey, new AttributeValue(item.getKey()),
        versionAttribute, new AttributeValue().withN(String.valueOf(item.getVersion())),
        itemJsonAttribute, new AttributeValue(json)
	  );
	}
	
	private <T extends VersionedData> T unmarshalItem(VersionedDataKind<T> kind, Map<String, AttributeValue> item) {
	  if (item == null || item.size() == 0) {
	    return null;
	  }
	  AttributeValue jsonAttr = item.get(itemJsonAttribute);
	  if (jsonAttr == null || jsonAttr.getS() == null) {
	    throw new IllegalStateException("DynamoDB map did not contain expected item string");
	  }
	  return FeatureStoreHelpers.unmarshalJson(kind, jsonAttr.getS());
	}
	
	Iterable<QueryResult> paginateQuery(final QueryRequest request) {
	  return new Iterable<QueryResult>() {
	    public Iterator<QueryResult> iterator() {
	      return new QueryIterator(request);
	    }
	  };
	}
	
	private class QueryIterator implements Iterator<QueryResult> {
	  private boolean eof;
	  private QueryRequest request;
	  
	  QueryIterator(QueryRequest request) {
	    this.request = request;
	  }
	  
    @Override
    public boolean hasNext() {
      return !eof;
    }

    @Override
    public QueryResult next() {
      if (eof) {
        return null;
      }
      QueryResult result = client.query(request);
      if (result.getLastEvaluatedKey() != null) {
        request.setExclusiveStartKey(result.getLastEvaluatedKey());
      } else {
        eof = true;
      }
      return result;
    }
	}
	
	static void batchWriteRequests(AmazonDynamoDB client, String tableName, List<WriteRequest> requests) {
	  int batchSize = 25;
	  for (int i = 0; i < requests.size(); i += batchSize) {
	    int limit = (i + batchSize < requests.size()) ? (i + batchSize) : requests.size();
	    List<WriteRequest> batch = requests.subList(i, limit); 
	    Map<String, List<WriteRequest>> batchMap = ImmutableMap.of(tableName, batch);
	    client.batchWriteItem(batchMap);
	  }
	}
}
