/**
 *    Copyright 2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.caches.ignite;

import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Ignite Test Case.
 *
 * @version $Id$
 */
public final class IgniteCacheAdapterTestCase {
  private static final String DEFAULT_ID = "Ignite";

  private static IgniteCacheAdapter cache;

  private static int TEST_OBJ_NUM = 1000;

  @BeforeClass
  public static void newCache() {
    cache = new IgniteCacheAdapter(DEFAULT_ID);
  }

  @Test
  public void shouldDemonstrateCopiesAreKeptAndEqual() {
    for (int i = 0; i < TEST_OBJ_NUM; i++) {
      cache.putObject(i, i);
      assertEquals(i, cache.getObject(i));
    }
    assertEquals(TEST_OBJ_NUM, cache.getSize());
  }

  @Test
  public void shouldRemoveItemOnDemand() {
    cache.putObject(0, 0);
    assertNotNull(cache.getObject(0));
    cache.removeObject(0);
    assertNull(cache.getObject(0));
  }

  @Test
  public void shouldFlushAllItemsOnDemand() {
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

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotCreateCache() {
    cache = new IgniteCacheAdapter(null);
  }

  @Test
  public void shouldCreateDefaultCache() throws Exception {
    String cfgPath = "config/default-config.xml";

    File cfgFile = new File(cfgPath);
    File cfgBkpFile = new File(cfgPath + ".bkp");

    if (cfgFile.renameTo(cfgBkpFile)) {
      try {
        new IgniteCacheAdapter(DEFAULT_ID);
      } finally {
        cfgBkpFile.renameTo(cfgFile);
      }
    } else
      throw new Exception("Failed to rename config file!");
  }

  @Test
  public void shouldVerifyCacheId() {
    assertEquals(DEFAULT_ID, cache.getId());
  }
}
