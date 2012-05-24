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



public abstract class CacheCallback<T> {

    private final boolean concurrentAccess;

    public CacheCallback() {
        this(false);
    }

    public CacheCallback(boolean concurrentAccess) {
        this.concurrentAccess = concurrentAccess;
    }

    /**
     * This method of api in <b>beta</b> stage
     *
     */
    protected void commonParams(ParamsCommand command, T object) {

    }

    protected void onInsertOrDelete(InsertOrDeleteCommand command, T object) {

    }

    /**
     * This method of api in <b>beta</b> stage
     *
     */
    protected void onUpdate(UpdateCommand command, T object) {

    }

    boolean isConcurrentAccess() {
        return concurrentAccess;
    }

}
