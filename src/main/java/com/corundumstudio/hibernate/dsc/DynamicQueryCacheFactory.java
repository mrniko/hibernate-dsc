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

import java.lang.reflect.Field;
import java.util.Properties;

import org.hibernate.HibernateException;
import org.hibernate.cache.spi.QueryCache;
import org.hibernate.cache.spi.QueryCacheFactory;
import org.hibernate.cache.spi.QueryKey;
import org.hibernate.cache.internal.StandardQueryCache;
import org.hibernate.cache.spi.UpdateTimestampsCache;
import org.hibernate.cfg.Settings;

public class DynamicQueryCacheFactory implements QueryCacheFactory {

    // TODO remove when https://hibernate.onjira.com/browse/HHH-5881 will be resolved
    private final Field positionalParameterField;

    public DynamicQueryCacheFactory() throws SecurityException, NoSuchFieldException {
        positionalParameterField = QueryKey.class.getDeclaredField("namedParameters");
        positionalParameterField.setAccessible(true);
    }

    @Override
    public QueryCache getQueryCache(String regionName, UpdateTimestampsCache updateTimestampsCache,
            Settings settings, Properties props) throws HibernateException {
        if (regionName == null) {
            return new StandardQueryCache(settings, props, updateTimestampsCache, regionName);
        }
        return new DynamicQueryCache(settings, props, updateTimestampsCache, regionName, positionalParameterField);
    }

}
