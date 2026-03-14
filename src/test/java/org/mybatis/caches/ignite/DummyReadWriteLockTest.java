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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DummyReadWriteLock} and its inner {@link DummyReadWriteLock.DummyLock}.
 */
class DummyReadWriteLockTest {

  private DummyReadWriteLock rwLock;

  @BeforeEach
  void setUp() {
    rwLock = new DummyReadWriteLock();
  }

  @Test
  void readLockReturnsNonNull() {
    assertNotNull(rwLock.readLock());
  }

  @Test
  void writeLockReturnsNonNull() {
    assertNotNull(rwLock.writeLock());
  }

  @Test
  void readLockAndWriteLockReturnSameInstance() {
    assertSame(rwLock.readLock(), rwLock.writeLock());
  }

  @Test
  void dummyLockLockDoesNotThrow() {
    Lock lock = rwLock.readLock();
    lock.lock();
    // no exception expected
  }

  @Test
  void dummyLockLockInterruptiblyDoesNotThrow() throws InterruptedException {
    Lock lock = rwLock.readLock();
    lock.lockInterruptibly();
    // no exception expected
  }

  @Test
  void dummyLockTryLockReturnsTrue() {
    Lock lock = rwLock.readLock();
    assertTrue(lock.tryLock());
  }

  @Test
  void dummyLockTryLockWithTimeoutReturnsTrue() throws InterruptedException {
    Lock lock = rwLock.readLock();
    assertTrue(lock.tryLock(1, TimeUnit.SECONDS));
  }

  @Test
  void dummyLockUnlockDoesNotThrow() {
    Lock lock = rwLock.readLock();
    lock.unlock();
    // no exception expected
  }

  @Test
  void dummyLockNewConditionReturnsNull() {
    Lock lock = rwLock.readLock();
    assertNull(lock.newCondition());
  }
}
