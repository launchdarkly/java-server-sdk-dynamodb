package com.launchdarkly.sdk.server.integrations;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.sdk.server.integrations.TestUtils.baseBuilder;
import static com.launchdarkly.sdk.server.integrations.TestUtils.clearEverything;
import static com.launchdarkly.sdk.server.integrations.TestUtils.createTableIfNecessary;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

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
  private static final String BAD_ITEM_KEY = "baditem";
  
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

  @Test
  public void dataStoreSkipsAndLogsTooLargeItemOnInitForFlag() throws Exception {
    dataStoreSkipsAndLogsTooLargeItemOnInit(DataModel.FEATURES, 0);
  }

  @Test
  public void dataStoreSkipsAndLogsTooLargeItemOnInitForSegment() throws Exception {
    dataStoreSkipsAndLogsTooLargeItemOnInit(DataModel.SEGMENTS, 1);
  }

  @Test
  public void dataStoreSkipsAndLogsTooLargeItemOnUpsertForFlag() throws Exception {
    dataStoreSkipsAndLogsTooLargeItemOnUpsert(DataModel.FEATURES);
  }

  @Test
  public void dataStoreSkipsAndLogsTooLargeItemOnUpsertForSegment() throws Exception {
    dataStoreSkipsAndLogsTooLargeItemOnUpsert(DataModel.SEGMENTS);
  }

  private void dataStoreSkipsAndLogsTooLargeItemOnInit(
      DataKind dataKind,
      int collIndex
      ) throws Exception {
    SerializedItemDescriptor badItem = makeTooBigItem(dataKind);
    
    FullDataSet<SerializedItemDescriptor> goodData = makeGoodData();
    List<Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>>> dataPlusBadItem =
        Lists.newArrayList(goodData.getData());
    List<Map.Entry<String, SerializedItemDescriptor>> items = Lists.newArrayList(
        dataPlusBadItem.get(collIndex).getValue().getItems());
    items.add(0, new SimpleEntry<>(BAD_ITEM_KEY, badItem));
    // put the bad item first to prove that items after that one are still stored
    dataPlusBadItem.set(collIndex, new SimpleEntry<>(dataKind, new KeyedItems<>(items)));
        
    try (PersistentDataStore store = makeStore()) {
      store.init(new FullDataSet<>(dataPlusBadItem));

      // init should not have thrown an exception, just logged an error and stored all the other items
      assertDataSetsEqual(goodData, getAllData(store));
    }
  }
  
  private void dataStoreSkipsAndLogsTooLargeItemOnUpsert(
      DataKind dataKind
      ) throws Exception {
    FullDataSet<SerializedItemDescriptor> goodData = makeGoodData();
    
    try (PersistentDataStore store = makeStore()) {
      store.init(goodData);
      
      assertDataSetsEqual(goodData, getAllData(store));
      
      store.upsert(dataKind, BAD_ITEM_KEY, makeTooBigItem(dataKind));

      // upsert should not have thrown an exception, just logged an error and skipped storing that update
      assertDataSetsEqual(goodData, getAllData(store));
    }
  }
  
  private static FullDataSet<SerializedItemDescriptor> makeGoodData() {
    return new FullDataSet<SerializedItemDescriptor>(ImmutableList.of(
         new SimpleEntry<>(DataModel.FEATURES,
             new KeyedItems<SerializedItemDescriptor>(ImmutableList.of(
                 new SimpleEntry<>("flag1",
                     new SerializedItemDescriptor(1, false, "{\"key\": \"flag1\", \"version\": 1}")),
                 new SimpleEntry<>("flag2",
                     new SerializedItemDescriptor(1, false, "{\"key\": \"flag2\", \"version\": 1}"))
                 ))),
         new SimpleEntry<>(DataModel.SEGMENTS,
             new KeyedItems<SerializedItemDescriptor>(ImmutableList.of(
                 new SimpleEntry<>("segment1",
                     new SerializedItemDescriptor(1, false, "{\"key\": \"segment1\", \"version\": 1}")),
                 new SimpleEntry<>("segment2",
                     new SerializedItemDescriptor(1, false, "{\"key\": \"segment2\", \"version\": 1}"))
                 )))
         ));
  }

  private static SerializedItemDescriptor makeTooBigItem(DataKind dataKind) {
    StringBuilder tooBigKeysListJson = new StringBuilder().append('[');
    for (int i = 0; i < 40000; i++) {
      if (i != 0 ) {
        tooBigKeysListJson.append(',');
      }
      tooBigKeysListJson.append("\"key").append(i).append('"');
    }
    tooBigKeysListJson.append(']');
    assertThat(tooBigKeysListJson.length(), greaterThan(400 * 1024));

    String serializedItem;
    if (dataKind == DataModel.SEGMENTS) {
      serializedItem = "{\":key\":\"" + BAD_ITEM_KEY + "\", \"version\": 1, \"included\": " +
          tooBigKeysListJson.toString() + "}";
    } else {
      serializedItem = "{\":key\":\"" + BAD_ITEM_KEY + "\", \"version\": 1, \"targets\": [{\"variation\": 0, \"values\":" +
        tooBigKeysListJson.toString() + "}]}";
    }
    return new SerializedItemDescriptor(1, false, serializedItem);
  }
  
  private static FullDataSet<SerializedItemDescriptor> getAllData(PersistentDataStore store) {
    List<Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>>> colls = new ArrayList<>();
    for (DataKind kind: new DataKind[] { DataModel.FEATURES, DataModel.SEGMENTS }) {
      KeyedItems<SerializedItemDescriptor> items = store.getAll(kind);
      colls.add(new SimpleEntry<>(kind, items));
    }
    return new FullDataSet<>(colls);
  }
  
  private static void assertDataSetsEqual(FullDataSet<SerializedItemDescriptor> expected,
      FullDataSet<SerializedItemDescriptor> actual) {
    if (!actual.equals(expected)) {
      throw new AssertionError("expected " + describeDataSet(expected) + " but got " +
          describeDataSet(actual));
    }
  }

  private static String describeDataSet(FullDataSet<SerializedItemDescriptor> data) {
    return Joiner.on(", ").join(
        Iterables.transform(data.getData(), entry -> {
          DataKind kind = entry.getKey();
          return "{" + kind + ": [" +
            Joiner.on(", ").join(
                Iterables.transform(entry.getValue().getItems(), item -> item.getValue().getSerializedItem())
                ) +
              "]}";
        }));
  }
}
