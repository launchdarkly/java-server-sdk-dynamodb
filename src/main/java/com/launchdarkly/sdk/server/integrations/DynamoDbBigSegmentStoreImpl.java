package com.launchdarkly.sdk.server.integrations;

import static com.launchdarkly.sdk.server.integrations.CollectionHelpers.mapOf;
import static com.launchdarkly.sdk.server.interfaces.BigSegmentStoreTypes.createMembershipFromSegmentRefs;

import com.launchdarkly.sdk.server.interfaces.BigSegmentStore;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreTypes;

import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

public class DynamoDbBigSegmentStoreImpl extends DynamoDbStoreImplBase implements BigSegmentStore {
  private final static String MEMBERSHIP_KEY = "big_segments_user";
  private final static String INCLUDED_ATTR = "included";
  private final static String EXCLUDED_ATTR = "excluded";

  private final static String METADATA_KEY = "big_segments_metadata";
  private final static String SYNC_TIME_ATTR = "synchronizedOn";

  public DynamoDbBigSegmentStoreImpl(DynamoDbClient client, String tableName, String prefix) {
    super(client, tableName, prefix);
  }

  protected Map<String, AttributeValue> makeKeysMap(String partitionKey, String sortKey) {
    return mapOf(
        PARTITION_KEY, AttributeValue.builder().s(partitionKey).build(),
        SORT_KEY, AttributeValue.builder().s(sortKey).build());
  }

  @Override
  public BigSegmentStoreTypes.Membership getMembership(String userHash) {
    String namespaceKey = prefixedNamespace(MEMBERSHIP_KEY);
    GetItemRequest request = GetItemRequest.builder()
        .tableName(tableName)
        .key(makeKeysMap(namespaceKey, userHash))
        .consistentRead(true)
        .build();
    GetItemResponse response = client.getItem(request);
    if (response == null || response.item().isEmpty()) {
      return null;
    }
    List<String> includedRefs = stringListFromAttrValue(response.item().get(INCLUDED_ATTR));
    List<String> excludedRefs = stringListFromAttrValue(response.item().get(EXCLUDED_ATTR));
    return createMembershipFromSegmentRefs(includedRefs, excludedRefs);
  }

  private static List<String> stringListFromAttrValue(AttributeValue attrValue) {
    return attrValue == null ? null : attrValue.ss();
  }

  @Override
  public BigSegmentStoreTypes.StoreMetadata getMetadata() {
    String key = prefixedNamespace(METADATA_KEY);
    GetItemRequest request = GetItemRequest.builder()
        .tableName(tableName)
        .key(makeKeysMap(key, key))
        .consistentRead(true)
        .build();
    GetItemResponse response = client.getItem(request);
    if (response == null) {
      return null;
    }
    AttributeValue syncTimeValue = response.item().get(SYNC_TIME_ATTR);
    if (syncTimeValue == null) {
      return null;
    }
    String syncTimeString = syncTimeValue.n();
    if (syncTimeString == null) {
      return null;
    }
    return new BigSegmentStoreTypes.StoreMetadata(Long.parseLong(syncTimeString));
  }
}
