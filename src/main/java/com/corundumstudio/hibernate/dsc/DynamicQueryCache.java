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

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.cache.spi.QueryKey;
import org.hibernate.cache.internal.StandardQueryCache;
import org.hibernate.cache.spi.UpdateTimestampsCache;
import org.hibernate.cfg.Settings;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.TypedValue;
import org.hibernate.type.Type;
import org.hibernate.type.TypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicQueryCache extends StandardQueryCache {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Field positionalParameterField;

    public DynamicQueryCache(Settings settings, Properties props,
                UpdateTimestampsCache updateTimestampsCache, String regionName, Field positionalParameterField) {
        super(settings, props, updateTimestampsCache, regionName);
        this.positionalParameterField = positionalParameterField;
    }

    @Override
    public boolean put(QueryKey key, Type[] returnTypes, List result,
                            boolean isNaturalKeyLookup, SessionImplementor session)
                            throws HibernateException {
        boolean res = super.put(key, returnTypes, result, isNaturalKeyLookup, session);
        if (res) {
            Map<String, Object> values = extractValues(key);
            log.debug("put to " + getRegion().getName() + ", values: " + values);
            getRegion().put(values, new QueryCacheValue(key, returnTypes));
        }
        return res;
    }

    @Override
    public List get(QueryKey key, Type[] returnTypes,
                        boolean isNaturalKeyLookup, Set spaces, SessionImplementor session)
                        throws HibernateException {
        List res = super.get(key, returnTypes, isNaturalKeyLookup, spaces, session);
        // touch cache key, to avoid eviction
        Map<String, Object> values = extractValues(key);
        QueryCacheValue cacheValue = getQueryCacheValue(values);
        log.debug("get from " + getRegion().getName() + ", values: " + values + ", result: " + res);
        if (res != null && cacheValue == null) {
            log.warn("QueryCacheValue entry has gone from cache before result for region: {}", getRegion().getName());
            getRegion().put(values, new QueryCacheValue(key, returnTypes));
        }
        return res;
    }

    private Map<String, Object> extractValues(QueryKey key) {
        try {
            Map<String, Object> paramValues = (Map<String, Object>) positionalParameterField.get(key);
            Map<String, Object> values = new HashMap<String, Object>(paramValues.size());
            for (Map.Entry<String, Object> entry : paramValues.entrySet()) {
                TypedValue typeValue = (TypedValue) entry.getValue();
                values.put(entry.getKey(), typeValue.getValue());
            }
            return values;
        } catch (Exception e) {
            throw new HibernateException(e);
        }
    }

    private QueryCacheValue getQueryCacheValue(Map<String, Object> values) {
        return (QueryCacheValue) getRegion().get(values);
    }

    private QueryCacheValue getQueryCacheTrasformed(Map<String, Object> values) {
        // TODO refactor it!
        String key = null;
        for (Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof Collection) {
                int i = 0;
                for (Object value : (Collection)entry.getValue()) {
                    values.put(entry.getKey() + i + "_", value);
                    i++;
                }
                key = entry.getKey();
                break;
            }
        }
        if (key != null) {
            values.remove(key);
        }
        return (QueryCacheValue) getRegion().get(values);
    }


    public void addResult(Map<String, Object> queryParams, Object queryResult, boolean uniqueResult, SessionImplementor session) {
        QueryCacheValue entry = getQueryCacheTrasformed(queryParams);
        if (entry == null) {
            return;
        }
        List<Object> cacheable = (List<Object>)getRegion().get(entry.getKey());
        if (cacheable == null) {
            return;
        }
        if (uniqueResult) {
            Object ts = cacheable.get(0);
            cacheable.clear();
            cacheable.add(ts);
        }
        if (entry.getReturnTypes().length == 1) {
            Serializable cacheObject = entry.getReturnTypes()[0].disassemble(queryResult, session, null);
            cacheable.add(cacheObject);
        } else {
            Serializable[] cacheObject =
                TypeHelper.disassemble((Object[])queryResult, entry.getReturnTypes(), null, session, null);
            cacheable.add(cacheObject);
        }
        getRegion().put(entry.getKey(), cacheable);
    }

    public void removeResult(Map<String, Object> queryParams, Object queryResult, SessionImplementor session) {
        QueryCacheValue entry = getQueryCacheTrasformed(queryParams);
        if (entry == null) {
            return;
        }
        List<Object> cacheList = (List<Object>)getRegion().get(entry.getKey());
        if (cacheList == null) {
            return;
        }
        if (entry.getReturnTypes().length == 1) {
            Serializable deletedObject = entry.getReturnTypes()[0].disassemble(queryResult, session, null);
            cacheList.remove(deletedObject);
        } else {
            Serializable[] deletedObject =
                TypeHelper.disassemble((Object[])queryResult, entry.getReturnTypes(), null, session, null);
            for (Iterator<Object> iterator = cacheList.iterator(); iterator.hasNext();) {
                Serializable[] object = (Serializable[]) iterator.next();
                if (Arrays.equals(object, deletedObject)) {
                    iterator.remove();
                }
            }
        }
        getRegion().put(entry.getKey(), cacheList);
    }

}
