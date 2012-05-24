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
package com.corundumstudio.hibernate.dsc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.corundumstudio.concurrent.CompositeLock;
import com.corundumstudio.concurrent.ConcurrentWeakLockMap;

/**
 * ListnerValue - object per query
 *
 * @author nkoksharov
 *
 */
class QueryListenerEntry {

    private final CacheCallback<?> handler;
    private final ConcurrentMap<String, ConcurrentWeakLockMap<Object>> paramLocks =
                                                        new ConcurrentHashMap<String, ConcurrentWeakLockMap<Object>>();

    QueryListenerEntry(String regionName, CacheCallback<?> handler) {
        this.handler = handler;
    }

    Lock getLock(Map<String, Object> params) {
        List<ReentrantLock> locks = new ArrayList<ReentrantLock>();
        for (Entry<String, Object> paramEntry : params.entrySet()) {
            ConcurrentWeakLockMap<Object> lockMap = paramLocks.get(paramEntry.getKey());
            if (lockMap == null) {
                lockMap = new ConcurrentWeakLockMap<Object>(16, 100);
                ConcurrentWeakLockMap<Object> oldLock = paramLocks.putIfAbsent(paramEntry.getKey(), lockMap);
                if (oldLock != null) {
                    lockMap = oldLock;
                }
            }
            ReentrantLock lock = lockMap.getLock(paramEntry.getValue());
            locks.add(lock);
        }
        return new CompositeLock(locks);
    }

    CacheCallback<Object> getHandler() {
        return (CacheCallback<Object>) handler;
    }

}
