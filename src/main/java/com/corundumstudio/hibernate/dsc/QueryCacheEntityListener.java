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

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.tuple.entity.EntityMetamodel;


public class QueryCacheEntityListener implements PostInsertEventListener,
                                                    PostDeleteEventListener, PostUpdateEventListener {

    private static final long serialVersionUID = 5902147771442167289L;

    private final ConcurrentMap<Class<?>, ConcurrentMap<String, QueryListenerEntry>> map =
                       new ConcurrentHashMap<Class<?>, ConcurrentMap<String, QueryListenerEntry>>();

    public <T> void register(Class<T> clazz, String regionName, CacheCallback<T> handler) {
        ConcurrentMap<String, QueryListenerEntry> values = map.get(clazz);
        if (values == null) {
            values = new ConcurrentHashMap<String, QueryListenerEntry>();
            ConcurrentMap<String, QueryListenerEntry> oldValues = map.putIfAbsent(clazz, values);
            if (oldValues != null) {
                values = oldValues;
            }
        }
        QueryListenerEntry entry = values.get(regionName);
        if (entry == null) {
            entry = new QueryListenerEntry(regionName, handler);
            values.putIfAbsent(regionName, entry);
        }
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Set<Entry<String, QueryListenerEntry>> values = getValue(event.getPersister());
        for (Entry<String, QueryListenerEntry> entry : values) {
            InsertOrDeleteCommand command = new InsertOrDeleteCommand();
            CacheCallback handler = entry.getValue().getHandler();
            handler.commonParams(command, event.getEntity());
            handler.onInsertOrDelete(command, event.getEntity());

            DynamicQueryCache queryCache = getQueryCache(event.getPersister(), entry.getKey(), entry.getValue());
            addResult(queryCache, entry.getValue(), command, event.getSession());
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Set<Entry<String, QueryListenerEntry>> values = getValue(event.getPersister());
        for (Entry<String, QueryListenerEntry> entry : values) {
            InsertOrDeleteCommand command = new InsertOrDeleteCommand();
            CacheCallback handler = entry.getValue().getHandler();
            handler.commonParams(command, event.getEntity());
            handler.onInsertOrDelete(command, event.getEntity());

            DynamicQueryCache queryCache = getQueryCache(event.getPersister(), entry.getKey(), entry.getValue());
            removeResult(queryCache, entry.getValue(), command, event.getSession());
        }
    }

    private void removeResult(DynamicQueryCache queryCache, QueryListenerEntry listenerEntry,
                        InsertOrDeleteCommand builder, SessionImplementor session) {
        Lock lock = null;
        if (listenerEntry.getHandler().isConcurrentAccess()) {
            lock = listenerEntry.getLock(builder.getParams());
            lock.lock();
        }
        try {
            queryCache.removeResult(builder.getParams(), builder.getResult(), session);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    private void addResult(DynamicQueryCache queryCache, QueryListenerEntry listenerEntry,
                        InsertOrDeleteCommand command, SessionImplementor session) {
        Lock lock = null;
        if (listenerEntry.getHandler().isConcurrentAccess()) {
            lock = listenerEntry.getLock(command.getParams());
            lock.lock();
        }
        try {
            queryCache.addResult(command.getParams(), command.getResult(), command.isUniqueResult(), session);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }


    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Set<Entry<String, QueryListenerEntry>> values = getValue(event.getPersister());
        for (Entry<String, QueryListenerEntry> entry : values) {
            UpdateCommand command = new UpdateCommand();
            CacheCallback handler = entry.getValue().getHandler();
            handler.commonParams(command, event.getEntity());
            handler.onUpdate(command, event.getEntity());

            DynamicQueryCache queryCache = getQueryCache(event.getPersister(), entry.getKey(), entry.getValue());
            if (command.isAddResult() || command.isUniqueResult()) {
                addResult(queryCache, entry.getValue(), command, event.getSession());
            }
            if (command.isRemoveResult()) {
                removeResult(queryCache, entry.getValue(), command, event.getSession());
            }
        }
    }

    private DynamicQueryCache getQueryCache(EntityPersister persister,
                                    String regionName, QueryListenerEntry listenerValue) {
        return (DynamicQueryCache) persister.getFactory().getQueryCache(regionName);
    }

    private Set<Entry<String, QueryListenerEntry>> getValue(EntityPersister persister) {
        EntityMetamodel entityMetamodel = persister.getEntityMetamodel();
        Class<?> clazz = entityMetamodel.getEntityType().getReturnedClass();
        Map<String, QueryListenerEntry> values = map.get(clazz);
        if (values == null) {
            return Collections.emptySet();
        }
        return values.entrySet();
    }

}
