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

import junit.framework.Assert;

import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.type.LongType;
import org.junit.BeforeClass;
import org.junit.Test;

import com.corundumstudio.hibernate.dsc.CacheCallback;
import com.corundumstudio.hibernate.dsc.InsertOrDeleteCommand;

public class InsertDeleteUniqueResultTest extends BaseTest {

    private static final String cacheRegion = "SimpleEntitySQL";

    @BeforeClass
    public static void before() {
        initHibernate();
        registerListener();
    }

    private static void registerListener() {
        CacheCallback<SimpleEntity> handler = new CacheCallback<SimpleEntity>() {

            @Override
            protected void onInsertOrDelete(InsertOrDeleteCommand command,
                    SimpleEntity object) {
                command.setParameter("phone", object.getPhone());
                command.setUniqueResult(object.getId());
            }

        };
        queryCacheEntityListener.register(SimpleEntity.class, cacheRegion, handler);
    }

    private void checkQueryResult(SimpleEntity entity, Long expected) {
        Session session = sessionFactory.openSession();
        SQLQuery query = session.createSQLQuery("SELECT id FROM SimpleEntity WHERE phone = :phone");
        query.addScalar("id", LongType.INSTANCE);
        query.setCacheable(true);
        query.setCacheRegion(cacheRegion);
        query.setParameter("phone", entity.getPhone());
        Long res = (Long) query.uniqueResult();
        Assert.assertEquals(expected, res);
        session.close();
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
    public void testEmptyCache() {
        sessionFactory.getCache().evictQueryRegions();

        SimpleEntity entity = new SimpleEntity();
        entity.setId(1L);
        entity.setPhone("1231232");
        entity.setAddress("Moscow city");

        checkQueryResult(entity, null);
        store(entity);
        checkQueryResult(entity, 1L);
        delete(entity);
        checkQueryResult(entity, null);
    }

    @Test
    public void testNonEmptyCache() {
        sessionFactory.getCache().evictQueryRegions();

        SimpleEntity entity = new SimpleEntity();
        entity.setId(1L);
        entity.setPhone("1231232");
        entity.setAddress("Moscow city");

        store(entity);
        checkQueryResult(entity, 1L);
        delete(entity);
        checkQueryResult(entity, null);
    }

}
