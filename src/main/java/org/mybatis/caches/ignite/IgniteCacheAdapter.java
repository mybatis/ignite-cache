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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ignite.catalog.ColumnType;
import org.apache.ignite.catalog.definitions.ColumnDefinition;
import org.apache.ignite.catalog.definitions.TableDefinition;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;

/**
 * Cache adapter for Ignite 3. Connects to a running Ignite 3 cluster via thin client. The server address is read from
 * {@value #CFG_PATH} (property {@code ignite.addresses}), otherwise the default {@value #DEFAULT_ADDRESSES} is used.
 *
 * @author Roman Shtykh
 */
public final class IgniteCacheAdapter implements Cache {

  /** Logger. */
  private static final Log log = LogFactory.getLog(IgniteCacheAdapter.class);

  /** Cache id. */
  private final String id;

  /** Table name derived from the cache id. */
  private final String tableName;

  /**
   * {@code ReadWriteLock}.
   */
  private final ReadWriteLock readWriteLock = new DummyReadWriteLock();

  /** Ignite thin client (shared across all adapter instances). Lazily initialized. */
  private static volatile IgniteClient sharedClient;

  /** This adapter's Ignite client. */
  private final IgniteClient client;

  /** Key-value view for this cache's table. */
  private final KeyValueView<Tuple, Tuple> cache;

  /** Default Ignite 3 thin client port. */
  static final String DEFAULT_ADDRESSES = "127.0.0.1:10800";

  /** Ignite client configuration file path. */
  static final String CFG_PATH = "config/default-config.properties";

  /** Table key column name. */
  static final String KEY_COL = "key";

  /** Table value column name. */
  static final String VAL_COL = "val";

  /**
   * Returns the shared {@link IgniteClient}, creating it lazily on first call.
   */
  private static IgniteClient getOrCreateIgniteClient() {
    if (sharedClient == null) {
      synchronized (IgniteCacheAdapter.class) {
        if (sharedClient == null) {
          sharedClient = createIgniteClient();
        }
      }
    }
    return sharedClient;
  }

  /**
   * Creates a new {@link IgniteClient} from the configuration file or defaults.
   */
  static IgniteClient createIgniteClient() {
    String addresses = DEFAULT_ADDRESSES;
    Properties props = new Properties();
    try (InputStream is = Files.newInputStream(Path.of(CFG_PATH))) {
      props.load(is);
      addresses = props.getProperty("ignite.addresses", DEFAULT_ADDRESSES);
    } catch (IOException e) {
      log.debug("Ignite config file not found at '" + CFG_PATH + "', using defaults.");
      log.trace("" + e);
    }
    return IgniteClient.builder().addresses(addresses.split(",")).build();
  }

  /**
   * Constructor.
   *
   * @param id
   *          Cache id.
   */
  public IgniteCacheAdapter(String id) {
    this(requireNonNullId(id), getOrCreateIgniteClient());
  }

  private static String requireNonNullId(String id) {
    if (id == null) {
      throw new IllegalArgumentException("Cache instances require an ID");
    }
    return id;
  }

  /**
   * Package-private constructor for testing: allows injection of a mock {@link IgniteClient} without requiring a
   * running Ignite cluster.
   *
   * @param id
   *          Cache id.
   * @param igniteClient
   *          The {@link IgniteClient} to use.
   */
  IgniteCacheAdapter(String id, IgniteClient igniteClient) {
    this.id = requireNonNullId(id);
    this.tableName = toTableName(id);
    this.client = igniteClient;

    igniteClient.catalog().createTable(
        TableDefinition.builder(tableName).ifNotExists().columns(ColumnDefinition.column(KEY_COL, ColumnType.VARBINARY),
            ColumnDefinition.column(VAL_COL, ColumnType.VARBINARY)).primaryKey(KEY_COL).build());

    cache = igniteClient.tables().table(tableName).keyValueView();
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public void putObject(Object key, Object value) {
    cache.put(null, Tuple.create().set(KEY_COL, serialize(key)), Tuple.create().set(VAL_COL, serialize(value)));
  }

  @Override
  public Object getObject(Object key) {
    Tuple valueTuple = cache.get(null, Tuple.create().set(KEY_COL, serialize(key)));
    return valueTuple != null ? deserialize(valueTuple.bytesValue(VAL_COL)) : null;
  }

  @Override
  public Object removeObject(Object key) {
    Tuple valueTuple = cache.getAndRemove(null, Tuple.create().set(KEY_COL, serialize(key)));
    return valueTuple != null ? deserialize(valueTuple.bytesValue(VAL_COL)) : null;
  }

  @Override
  public void clear() {
    cache.removeAll(null);
  }

  @Override
  public int getSize() {
    try (ResultSet<SqlRow> rs = client.sql().execute(null, "SELECT COUNT(*) FROM " + tableName)) {
      return rs.hasNext() ? (int) rs.next().longValue(0) : 0;
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return readWriteLock;
  }

  static String toTableName(String id) {
    // Sanitize to alphanumeric and underscore only, ensuring safe use in SQL identifiers.
    return id.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
  }

  static byte[] serialize(Object obj) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot serialize object of type " + obj.getClass().getName(), e);
    }
  }

  static Object deserialize(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais)) {
      return ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new IllegalStateException("Cannot deserialize cache object", e);
    }
  }
}
