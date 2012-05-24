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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.Assert;

import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.type.LongType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.corundumstudio.hibernate.dsc.CacheCallback;
import com.corundumstudio.hibernate.dsc.InsertOrDeleteCommand;

public class InsertDeleteListResultTest extends BaseTest {

    private static final String cacheRegion = "SimpleEntitySQL";
    private static final String multiCacheRegion = "MultiSimpleEntitySQL";
    private final String addressValue = "Moscow city";

    @BeforeClass
    public static void before() {
        initHibernate();
        registerListener();
    }

    @Before
    public void beforeEachTest() {
        sessionFactory.getCache().evictQueryRegions();
    }

    private static void registerListener() {
        queryCacheEntityListener.register(SimpleEntity.class, cacheRegion, new CacheCallback<SimpleEntity>() {
            @Override
            protected void onInsertOrDelete(InsertOrDeleteCommand command, SimpleEntity object) {
                command.setParameter("phone", object.getPhone());
                command.addResult(object.getId());
            }
        });

        queryCacheEntityListener.register(SimpleEntity.class, multiCacheRegion, new CacheCallback<SimpleEntity>() {
            @Override
            protected void onInsertOrDelete(InsertOrDeleteCommand command, SimpleEntity object) {
                command.setParameter("address", object.getAddress());
                command.addResult(object.getId());
            }
        });
    }

    private void checkQueryResult(SimpleEntity entity, List expected) {
        Session session = sessionFactory.openSession();
        SQLQuery query = session.createSQLQuery("SELECT id FROM SimpleEntity WHERE phone = :phone");
        query.addScalar("id", LongType.INSTANCE);
        query.setCacheable(true);
        query.setCacheRegion(cacheRegion);
        query.setParameter("phone", entity.getPhone());
        List res = query.list();
        Assert.assertEquals(expected, res);
        session.close();
    }

    private List listQueryResult() {
        Session session = sessionFactory.openSession();
        SQLQuery query = session.createSQLQuery("SELECT id FROM SimpleEntity WHERE address = :address");
        query.addScalar("id", LongType.INSTANCE);
        query.setCacheable(true);
        query.setCacheRegion(multiCacheRegion);
        query.setParameter("address", addressValue);
        List res = query.list();
        session.close();
        return res;
    }

    private void checkMultipleQueryResult(List expected) {
        List res = listQueryResult();
        Assert.assertEquals(expected, res);
    }


    private void store(SimpleEntity entity) {
        Session session1 = sessionFactory.openSession();
        Transaction tr1 = session1.beginTransaction();
        session1.save(entity);
        tr1.commit();
        session1.close();
    }

    private void delete(SimpleEntity entity) {
        Session session1 = sessionFactory.openSession();
        Transaction tr1 = session1.beginTransaction();
        session1.delete(entity);
        tr1.commit();
        session1.close();
    }

    @Test
    public void testMultipleResultCache() {
        SimpleEntity entity1 = new SimpleEntity();
        entity1.setId(1L);
        entity1.setPhone("123");
        entity1.setAddress(addressValue);
        store(entity1);

        SimpleEntity entity2 = new SimpleEntity();
        entity2.setId(2L);
        entity2.setPhone("432");
        entity2.setAddress(addressValue);
        store(entity2);

        SimpleEntity entity3 = new SimpleEntity();
        entity3.setId(3L);
        entity3.setPhone("890");
        entity3.setAddress("Tver city");
        store(entity3);

        checkMultipleQueryResult(Arrays.asList(1L, 2L));
        delete(entity1);
        checkMultipleQueryResult(Arrays.asList(2L));
        delete(entity2);
        checkMultipleQueryResult(Collections.emptyList());
        delete(entity3);
    }


    @Test
    public void testEmptyCache() {
        SimpleEntity entity = new SimpleEntity();
        entity.setId(1L);
        entity.setPhone("1231232");
        entity.setAddress("Any city");

        checkQueryResult(entity, Collections.emptyList());
        store(entity);
        checkQueryResult(entity, Arrays.asList(1L));
        delete(entity);
        checkQueryResult(entity, Collections.emptyList());
    }

    @Test
    public void testNonEmptyCache() {
        SimpleEntity entity = new SimpleEntity();
        entity.setId(1L);
        entity.setPhone("1231232");
        entity.setAddress("Moscow city");

        store(entity);
        checkQueryResult(entity, Arrays.asList(1L));
        delete(entity);
        checkQueryResult(entity, Collections.emptyList());
    }


}
