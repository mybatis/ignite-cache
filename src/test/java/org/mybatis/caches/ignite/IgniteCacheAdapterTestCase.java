/*
 *    Copyright 2016-2025 the original author or authors.
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

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ignite Test Case.
 */
class IgniteCacheAdapterTestCase {
  private static final String DEFAULT_ID = "Ignite";

  private static IgniteCacheAdapter cache;

  private static int TEST_OBJ_NUM = 1000;

  @BeforeAll
  static void newCache() {
    cache = new IgniteCacheAdapter(DEFAULT_ID);
  }

  @Test
  void shouldDemonstrateCopiesAreKeptAndEqual() {
    for (int i = 0; i < TEST_OBJ_NUM; i++) {
      cache.putObject(i, i);
      assertEquals(i, cache.getObject(i));
    }
    assertEquals(TEST_OBJ_NUM, cache.getSize());
  }

  @Test
  void shouldRemoveItemOnDemand() {
    cache.putObject(0, 0);
    assertNotNull(cache.getObject(0));
    cache.removeObject(0);
    assertNull(cache.getObject(0));
  }

  @Test
  void shouldFlushAllItemsOnDemand() {
    for (int i = 0; i < TEST_OBJ_NUM; i++) {
      cache.putObject(i, i);
    }
    for (int i = 0; i < TEST_OBJ_NUM; i++) {
      assertNotNull(cache.getObject(i));
    }
    cache.clear();
    for (int i = 0; i < TEST_OBJ_NUM; i++) {
      assertNull(cache.getObject(i));
    }
  }

  @Test
  void shouldNotCreateCache() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      cache = new IgniteCacheAdapter(null);
    });
  }

  @Test
  void shouldCreateDefaultCache() throws Exception {
    String cfgPath = "config/default-config.xml";

    Path cfgFile = Path.of(cfgPath);
    Path cfgBkpFile = Path.of(cfgPath + ".bkp");

    if (cfgFile.toFile().renameTo(cfgBkpFile.toFile())) {
      try {
        new IgniteCacheAdapter(DEFAULT_ID);
      } finally {
        if (!cfgBkpFile.toFile().renameTo(cfgFile.toFile())) {
          throw new Exception("Failed to rename config file!");
        }
      }
    } else
      throw new Exception("Failed to rename config file!");
  }

  @Test
  void shouldVerifyCacheId() {
    assertEquals(DEFAULT_ID, cache.getId());
  }
}
