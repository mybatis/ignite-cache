/*
 *    Copyright 2016-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.caches.ignite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.ignite.catalog.IgniteCatalog;
import org.apache.ignite.catalog.definitions.TableDefinition;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.IgniteSql;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.table.IgniteTables;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Table;
import org.apache.ignite.table.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link IgniteCacheAdapter} using Mockito mocks. These tests do not require a running Ignite cluster.
 */
@ExtendWith(MockitoExtension.class)
class IgniteCacheAdapterUnitTest {

  private static final String DEFAULT_ID = "Ignite";

  /** Expected table name: DEFAULT_ID uppercased (already alphanumeric). */
  private static final String TABLE_NAME = "IGNITE";

  @Mock
  private IgniteClient mockClient;

  @Mock
  private IgniteCatalog mockCatalog;

  @Mock
  private IgniteTables mockTables;

  @Mock
  private Table mockTable;

  @SuppressWarnings("unchecked")
  @Mock
  private KeyValueView<Tuple, Tuple> mockKvView;

  @Mock
  private IgniteSql mockSql;

  private IgniteCacheAdapter cache;

  @BeforeEach
  void setUp() {
    when(mockClient.catalog()).thenReturn(mockCatalog);
    when(mockClient.tables()).thenReturn(mockTables);
    when(mockTables.table(TABLE_NAME)).thenReturn(mockTable);
    when(mockTable.keyValueView()).thenReturn(mockKvView);

    cache = new IgniteCacheAdapter(DEFAULT_ID, mockClient);
  }

  @Test
  void shouldNotCreateCacheWithNullId() {
    assertThrows(IllegalArgumentException.class, () -> new IgniteCacheAdapter(null, mockClient));
  }

  @Test
  void shouldVerifyCacheId() {
    assertEquals(DEFAULT_ID, cache.getId());
  }

  @Test
  void shouldReturnReadWriteLock() {
    assertNotNull(cache.getReadWriteLock());
  }

  @Test
  void shouldPutObject() {
    cache.putObject("key1", "value1");

    verify(mockKvView).put(isNull(), any(Tuple.class), any(Tuple.class));
  }

  @Test
  void shouldGetObjectWhenPresent() {
    byte[] serializedValue = IgniteCacheAdapter.serialize("value1");
    Tuple returnedTuple = Tuple.create().set(IgniteCacheAdapter.VAL_COL, serializedValue);
    when(mockKvView.get(isNull(), any(Tuple.class))).thenReturn(returnedTuple);

    Object result = cache.getObject("key1");

    assertEquals("value1", result);
  }

  @Test
  void shouldGetObjectReturnsNullWhenMissing() {
    when(mockKvView.get(isNull(), any(Tuple.class))).thenReturn(null);

    assertNull(cache.getObject("missing"));
  }

  @Test
  void shouldRemoveObjectAndReturnPreviousValue() {
    byte[] serializedValue = IgniteCacheAdapter.serialize(42);
    Tuple returnedTuple = Tuple.create().set(IgniteCacheAdapter.VAL_COL, serializedValue);
    when(mockKvView.getAndRemove(isNull(), any(Tuple.class))).thenReturn(returnedTuple);

    Object result = cache.removeObject("key1");

    assertEquals(42, result);
    verify(mockKvView).getAndRemove(isNull(), any(Tuple.class));
  }

  @Test
  void shouldRemoveObjectReturnsNullWhenMissing() {
    when(mockKvView.getAndRemove(isNull(), any(Tuple.class))).thenReturn(null);

    assertNull(cache.removeObject("missing"));
  }

  @Test
  void shouldClearCache() {
    cache.clear();

    verify(mockKvView).removeAll(isNull());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGetSizeWithRows() {
    ResultSet<SqlRow> mockResultSet = mock(ResultSet.class);
    SqlRow mockRow = mock(SqlRow.class);
    when(mockRow.longValue(0)).thenReturn(7L);
    when(mockResultSet.hasNext()).thenReturn(true);
    when(mockResultSet.next()).thenReturn(mockRow);
    when(mockClient.sql()).thenReturn(mockSql);
    // Use doReturn to avoid calling the default interface method during stubbing
    doReturn(mockResultSet).when(mockSql).execute(isNull(), anyString());

    assertEquals(7, cache.getSize());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGetSizeReturnsZeroWhenEmpty() {
    ResultSet<SqlRow> mockResultSet = mock(ResultSet.class);
    when(mockResultSet.hasNext()).thenReturn(false);
    when(mockClient.sql()).thenReturn(mockSql);
    // Use doReturn to avoid calling the default interface method during stubbing
    doReturn(mockResultSet).when(mockSql).execute(isNull(), anyString());

    assertEquals(0, cache.getSize());
  }

  @Test
  void shouldCreateTableOnConstruction() {
    verify(mockCatalog).createTable(any(TableDefinition.class));
  }

  @Test
  void shouldToTableNameSanitizeSpecialChars() {
    assertEquals("ORG_FOO_BAR", IgniteCacheAdapter.toTableName("org.foo.bar"));
    assertEquals("MY_CACHE", IgniteCacheAdapter.toTableName("my-cache"));
    assertEquals("MYCACHE123", IgniteCacheAdapter.toTableName("mycache123"));
    assertEquals("___", IgniteCacheAdapter.toTableName("..."));
  }

  @Test
  void shouldSerializeAndDeserializeRoundtrip() {
    Object original = "hello world";
    byte[] bytes = IgniteCacheAdapter.serialize(original);
    assertNotNull(bytes);
    assertEquals(original, IgniteCacheAdapter.deserialize(bytes));
  }

  @Test
  void shouldDeserializeNullReturnsNull() {
    assertNull(IgniteCacheAdapter.deserialize(null));
  }

  @Test
  void shouldSerializeAndDeserializeInteger() {
    int original = 12345;
    byte[] bytes = IgniteCacheAdapter.serialize(original);
    assertEquals(original, IgniteCacheAdapter.deserialize(bytes));
  }

  @Test
  void shouldThrowOnSerializeNonSerializable() {
    // Thread is not serializable; serialize() should wrap the IOException
    assertThrows(IllegalArgumentException.class, () -> IgniteCacheAdapter.serialize(Thread.currentThread()));
  }

  @Test
  void shouldThrowOnDeserializeCorruptedBytes() {
    // Bytes that are not a valid Java serialization stream
    byte[] corrupted = { 0x00, 0x01, 0x02, 0x03 };
    assertThrows(IllegalStateException.class, () -> IgniteCacheAdapter.deserialize(corrupted));
  }

  @Test
  void shouldThrowWhenPublicConstructorReceivesNullId() {
    // Exercises the requireNonNullId path in the public constructor without connecting to Ignite
    assertThrows(IllegalArgumentException.class, () -> new IgniteCacheAdapter(null));
  }

  @Test
  void shouldUseExistingSharedClientWhenAlreadyInitialized() throws Exception {
    // Inject the already-configured mockClient as the static sharedClient so the public constructor
    // takes the non-null fast path through getOrCreateIgniteClient() without connecting to Ignite.
    java.lang.reflect.Field field = IgniteCacheAdapter.class.getDeclaredField("sharedClient");
    field.setAccessible(true);
    IgniteClient previous = (IgniteClient) field.get(null);
    field.set(null, mockClient);
    try {
      IgniteCacheAdapter adapter = new IgniteCacheAdapter(DEFAULT_ID);
      assertNotNull(adapter);
      assertEquals(DEFAULT_ID, adapter.getId());
    } finally {
      field.set(null, previous);
    }
  }

  @Test
  void shouldFallbackToDefaultAddressWhenConfigFileMissing() throws Exception {
    // Hide the config file so createIgniteClient() takes the IOException fallback branch,
    // then verify it throws because no Ignite server is reachable.
    java.nio.file.Path cfgFile = java.nio.file.Path.of(IgniteCacheAdapter.CFG_PATH);
    java.nio.file.Path cfgBkp = java.nio.file.Path.of(IgniteCacheAdapter.CFG_PATH + ".unit_bkp");
    org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(cfgFile), "Config file not present");
    java.nio.file.Files.move(cfgFile, cfgBkp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    try {
      // createIgniteClient() will log the IOException and fall back to DEFAULT_ADDRESSES;
      // build() then throws because no server is running.
      assertThrows(Exception.class, IgniteCacheAdapter::createIgniteClient);
    } finally {
      java.nio.file.Files.move(cfgBkp, cfgFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
