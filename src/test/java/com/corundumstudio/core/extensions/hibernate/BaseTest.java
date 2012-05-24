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
package com.corundumstudio.core.extensions.hibernate;

import java.util.Properties;

import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.junit.Ignore;

import com.corundumstudio.hibernate.dsc.DynamicQueryCacheFactory;
import com.corundumstudio.hibernate.dsc.QueryCacheEntityListener;

@Ignore
class BaseTest {

    private static final String DRIVER = "org.h2.Driver";
    private static final String URL = "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;MVCC=TRUE";
    private static final String USER = "sa";
    private static final String PASS = "";

    protected static QueryCacheEntityListener queryCacheEntityListener = new QueryCacheEntityListener();
    protected static SessionFactoryImplementor sessionFactory;

    private static Properties buildDatabaseConfiguration(String dbName) {
        Properties props = new Properties();
        props.put(Environment.DRIVER, DRIVER);
        props.put(Environment.URL, String.format(URL, dbName));
        props.put(Environment.USER, USER);
        props.put(Environment.PASS, PASS);
        return props;
    }

    protected static void initHibernate() {
        Properties props = buildDatabaseConfiguration("db1");

        Configuration cfg = new Configuration();
        cfg.setProperty(Environment.GENERATE_STATISTICS, "true");
        cfg.setProperty(AvailableSettings.HBM2DDL_AUTO, "create");
        cfg.setProperty(AvailableSettings.CACHE_REGION_FACTORY, InfinispanRegionFactory.class.getName());
        cfg.setProperty(InfinispanRegionFactory.INFINISPAN_CONFIG_RESOURCE_PROP, "infinispan.xml");
        cfg.setProperty(AvailableSettings.QUERY_CACHE_FACTORY, DynamicQueryCacheFactory.class.getName());
        cfg.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "true");
        cfg.setProperty(Environment.USE_QUERY_CACHE, "true");
        cfg.addAnnotatedClass(SimpleEntity.class);
        cfg.buildMappings();

        ServiceRegistryBuilder sb = new ServiceRegistryBuilder();
        ServiceRegistry serviceRegistry = sb.applySettings(props).buildServiceRegistry();
        sessionFactory = (SessionFactoryImplementor) cfg.buildSessionFactory(serviceRegistry);

        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(queryCacheEntityListener);
        registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(queryCacheEntityListener);
        registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(queryCacheEntityListener);
    }


}
