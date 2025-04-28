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

import java.io.File;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteIllegalStateException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

/**
 * Cache adapter for Ignite. Cache is initialized from IGNITE_HOME/config/default-config.xml settings, otherwise default
 * one is started.
 *
 * @author Roman Shtykh
 */
public final class IgniteCacheAdapter implements Cache {

  /** Logger. */
  private static final Log log = LogFactory.getLog(IgniteCacheAdapter.class);

  /** Cache id. */
  private final String id;

  /**
   * {@code ReadWriteLock}.
   */
  private final ReadWriteLock readWriteLock = new DummyReadWriteLock();

  /** Grid instance. */
  private static final Ignite ignite;

  /** Cache. */
  private final IgniteCache<Object, Object> cache;

  /** Ignite configuration file path. */
  private static final String CFG_PATH = "config/default-config.xml";

  static {
    boolean started = false;
    try {
      Ignition.ignite();
      started = true;
    } catch (IgniteIllegalStateException e) {
      log.debug("Using the Ignite instance that has been already started.");
      log.trace("" + e);
    }
    if (started) {
      ignite = Ignition.ignite();
    } else {
      ignite = Ignition.start();
    }
  }

  /**
   * Constructor.
   *
   * @param id
   *          Cache id.
   */
  @SuppressWarnings("unchecked")
  public IgniteCacheAdapter(String id) {
    if (id == null) {
      throw new IllegalArgumentException("Cache instances require an ID");
    }

    CacheConfiguration<Object, Object> cacheCfg = null;

    try {
      DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

      new XmlBeanDefinitionReader(factory).loadBeanDefinitions(new FileSystemResource(new File(CFG_PATH)));

      cacheCfg = (CacheConfiguration<Object, Object>) factory.getBean("templateCacheCfg");

      cacheCfg.setEvictionPolicyFactory(null);
      cacheCfg.setCacheLoaderFactory(null);
      cacheCfg.setCacheWriterFactory(null);

      // overrides template cache name with the specified id.
      cacheCfg.setName(id);
    } catch (NoSuchBeanDefinitionException | BeanDefinitionStoreException e) {
      // initializes the default cache.
      log.warn("Initializing the default cache. Consider properly configuring '" + CFG_PATH + "' instead.");
      log.trace("" + e);

      cacheCfg = new CacheConfiguration<>(id);
    }

    cache = ignite.getOrCreateCache(cacheCfg);

    this.id = id;
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public void putObject(Object key, Object value) {
    cache.put(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return cache.get(key);
  }

  @Override
  public Object removeObject(Object key) {
    return cache.remove(key);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public int getSize() {
    return cache.size(CachePeekMode.PRIMARY);
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return readWriteLock;
  }
}
