package com.launchdarkly.sdk.server.integrations;

import java.io.Closeable;
import java.io.IOException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

abstract class DynamoDbStoreImplBase implements Closeable {
  static final String PARTITION_KEY = "namespace";
  static final String SORT_KEY = "key";

  protected final DynamoDbClient client;
  protected final String tableName;
  protected final String prefix;

  public DynamoDbStoreImplBase(DynamoDbClient client, String tableName, String prefix) {
    this.client = client;
    this.tableName = tableName;
    this.prefix = "".equals(prefix) ? null : prefix;
  }

  protected String prefixedNamespace(String base) {
    return prefix == null ? base : (prefix + ":" + base);
  }

  public void close() throws IOException {
    client.close();
  }
}
