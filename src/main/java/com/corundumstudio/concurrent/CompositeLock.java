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

import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CompositeLock implements Lock {

    public final static long DEFAULT_LOCK_WAIT_TIMEOUT = 100;


    private final List<ReentrantLock> locks;
    private final long waitTimeout;

    public CompositeLock(List<ReentrantLock> locks) {
        this(DEFAULT_LOCK_WAIT_TIMEOUT, locks);
    }

    public CompositeLock(long waitTimeout, List<ReentrantLock> locks) {
        this.waitTimeout = waitTimeout;
        this.locks = locks;
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (!isHeldByCurrentThread()) {
            while (true) {
                if (tryLock(waitTimeout, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
        } else {
            tryLock();
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        ListIterator<ReentrantLock> listIterator = locks.listIterator();
        while (listIterator.hasNext()) {
            Lock lock = listIterator.next();
            if (!lock.tryLock()) {
                unlockAcuriedLocks(listIterator);
                return false;
            }
        }
        return true;
    }

    private void unlockAcuriedLocks(ListIterator<ReentrantLock> iterator) {
        iterator.previous();
        while (iterator.hasPrevious()) {
            Lock lock = iterator.previous();
            lock.unlock();
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
        ListIterator<ReentrantLock> listIterator = locks.listIterator();
        while (listIterator.hasNext()) {
            Lock lock = listIterator.next();
            if (!lock.tryLock(time, unit)) {
                unlockAcuriedLocks(listIterator);
                return false;
            }
        }
        return true;
    }

    boolean isHeldByCurrentThread() {
        for (ReentrantLock lock : locks) {
            if (!lock.isHeldByCurrentThread()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void unlock() {
        for (ReentrantLock lock : locks) {
            lock.unlock();
        }
    }

}
