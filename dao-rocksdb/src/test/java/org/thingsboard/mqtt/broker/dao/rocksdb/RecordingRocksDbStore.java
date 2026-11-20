/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.dao.rocksdb;

import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link RocksDbStore} that records the {@link StoreWriteClass} of every put, delete and batch it runs, counts
 * its point reads, and can fail its next batch.
 */
public class RecordingRocksDbStore extends RocksDbStore {

    private final List<StoreWriteClass> writeClasses = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicReference<RuntimeException> nextWriteFailure = new AtomicReference<>();

    public RecordingRocksDbStore(StoreProperties properties, StoreMemoryBudget memoryBudget) {
        super(properties, memoryBudget, TestDiskSpaceMonitors.roomy(properties));
    }

    @Override
    public void write(StoreWriteClass writeClass, Consumer<StoreBatch> body) {
        writeClasses.add(writeClass);
        RuntimeException failure = nextWriteFailure.getAndSet(null);
        if (failure != null) {
            throw failure;
        }
        super.write(writeClass, body);
    }

    /**
     * Makes the next {@link #write} throw {@code failure} without writing anything, as a refused or failed batch does.
     */
    public void failNextWrite(RuntimeException failure) {
        nextWriteFailure.set(failure);
    }

    @Override
    public void put(StoreWriteClass writeClass, StoreColumnFamily cf, byte[] key, byte[] value) {
        writeClasses.add(writeClass);
        super.put(writeClass, cf, key, value);
    }

    @Override
    public void delete(StoreWriteClass writeClass, StoreColumnFamily cf, byte[] key) {
        writeClasses.add(writeClass);
        super.delete(writeClass, cf, key);
    }

    @Override
    public byte[] get(StoreColumnFamily cf, byte[] key) {
        gets.incrementAndGet();
        return super.get(cf, key);
    }

    /**
     * The number of {@link #get} calls since the last call.
     */
    public int takeGets() {
        return gets.getAndSet(0);
    }

    /**
     * The classes recorded since the last call, in call order.
     */
    public List<StoreWriteClass> takeWriteClasses() {
        synchronized (writeClasses) {
            List<StoreWriteClass> taken = new ArrayList<>(writeClasses);
            writeClasses.clear();
            return taken;
        }
    }

}
