/**
 * Copyright 2012 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.corundumstudio.concurrent;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap;

public class ConcurrentWeakLockMap<K> {

    private final ConcurrentMap<K, ReentrantLock> lockMap;

    public ConcurrentWeakLockMap(int initialCapacity, int concurrencyLevel) {
        lockMap = new ConcurrentReferenceHashMap<K, ReentrantLock>(initialCapacity, .75f, concurrencyLevel,
                ConcurrentReferenceHashMap.ReferenceType.STRONG,
                ConcurrentReferenceHashMap.ReferenceType.WEAK, null);
    }

    public ReentrantLock getLock(K key) {
        ReentrantLock lock = lockMap.get(key);
        if (lock == null) {
            ReentrantLock newlock = new ReentrantLock();
            lock = lockMap.putIfAbsent(key, newlock);
            if (lock == null) {
                lock = newlock;
            }
        }
        return lock;
    }

}
