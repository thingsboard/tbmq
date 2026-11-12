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
package org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.AppendResult;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.DeviceQueueInfo;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreBatch;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * The DEVICE queues of the standalone edition in the {@code device_msg} and {@code device_msg_meta} column families.
 * Sequence and packet id advance together, one step per appended message (the packet id wraps 65,535 to 1, the
 * sequence never wraps), so an entry is found from its packet id in constant time:
 * {@code seq = lastSequence - ((lastPacketId - packetId + 65535) % 65535)}.
 * <p>
 * Every change of a queue runs under one of 256 striped locks keyed by the hash of the queue key;
 * a call that touches several queues takes their locks in ascending stripe order. Each call writes its entries and
 * metas in one atomic batch. A queue is dense when it has no holes ({@code count == lastSequence - firstSequence + 1});
 * trimming and advancing the head of a dense queue use arithmetic and point operations only, a queue with holes (out of
 * order acknowledgements) uses one bounded scan from its first sequence.
 */
@Component
public class RocksDbDeviceMsgQueueStore implements DeviceMsgQueueStore {

    static final int MAX_PACKET_ID = 0xFFFF;

    private static final int LOCK_STRIPES = 256;
    private static final StoreColumnFamily ENTRIES = StoreColumnFamily.DEVICE_MSG;
    private static final StoreColumnFamily METAS = StoreColumnFamily.DEVICE_MSG_META;

    private final RocksDbStore store;
    private final int limit;
    private final long ttlMs;
    private final Clock clock;
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    @Autowired
    public RocksDbDeviceMsgQueueStore(RocksDbStore store,
                                      @Value("${mqtt.persistent-session.device.persisted-messages.limit}") int limit,
                                      @Value("${mqtt.persistent-session.device.persisted-messages.ttl}") int ttlSeconds) {
        this(store, limit, ttlSeconds, Clock.systemUTC());
    }

    public RocksDbDeviceMsgQueueStore(RocksDbStore store, int limit, int ttlSeconds, Clock clock) {
        if (limit > MAX_PACKET_ID) {
            throw new IllegalArgumentException("Persisted messages limit can't be greater than 65535!");
        }
        this.store = store;
        this.limit = limit;
        this.ttlMs = ttlSeconds * 1000L;
        this.clock = clock;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @Override
    public List<AppendResult> append(List<QueueAppend> appends) {
        int[] stripes = appends.stream().mapToInt(append -> stripe(append.queueKey())).distinct().sorted().toArray();
        lockAll(stripes);
        try {
            long now = clock.millis();
            Map<String, PendingAppend> queues = new LinkedHashMap<>();
            List<AppendResult> results = new ArrayList<>(appends.size());
            for (QueueAppend append : appends) {
                PendingAppend queue = queues.computeIfAbsent(append.queueKey(), key -> new PendingAppend(key, loadMeta(key)));
                results.add(queue.add(append.messages(), now));
            }
            List<PendingAppend> changed = queues.values().stream().filter(PendingAppend::hasEntries).toList();
            changed.forEach(PendingAppend::planTrim);
            if (!changed.isEmpty()) {
                store.write(batch -> changed.forEach(queue -> queue.writeTo(batch)));
            }
            return results;
        } finally {
            unlockAll(stripes);
        }
    }

    @Override
    public List<StoredDeviceMsg> read(String queueKey, long afterSequence, int limit) {
        DeviceQueueMeta meta = loadMeta(queueKey);
        if (limit <= 0 || meta.count() == 0) {
            return List.of();
        }
        // starting at the head skips the tombstones of trimmed and acknowledged entries below it
        long from = Math.max(afterSequence, meta.firstSequence() - 1) + 1;
        return store.scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, from), DeviceMsgKeys.queueEnd(queueKey), iterator -> {
            List<StoredDeviceMsg> page = new ArrayList<>(Math.min(limit, meta.count()));
            while (iterator.isValid() && page.size() < limit) {
                long sequence = DeviceMsgKeys.sequenceOf(iterator.key());
                byte[] value = iterator.value();
                DeviceMsgEntryCodec.Header header = DeviceMsgEntryCodec.header(value);
                page.add(new StoredDeviceMsg(sequence, header.packetId(), header.packetType(), sequence <= meta.sentUpTo(),
                        header.createdTimeMs(), header.msgExpiresAtMs(), DeviceMsgEntryCodec.body(value)));
                iterator.next();
            }
            return page;
        });
    }

    @Override
    public void markSent(String queueKey, long upToSequence) {
        throw new UnsupportedOperationException("markSent is not supported yet");
    }

    @Override
    public boolean remove(String queueKey, int packetId) {
        ReentrantLock lock = lockFor(queueKey);
        lock.lock();
        try {
            DeviceQueueMeta meta = loadMeta(queueKey);
            Entry entry = find(queueKey, meta, packetId);
            if (entry == null) {
                return false;
            }
            int count = meta.count() - 1;
            long first = meta.firstSequence();
            if (count == 0) {
                first = meta.lastSequence() + 1;
            } else if (entry.sequence() == first) {
                first = isDense(meta) ? first + 1 : firstLiveSequence(queueKey, first + 1, meta.lastSequence());
            }
            DeviceQueueMeta updated = new DeviceQueueMeta(first, meta.lastSequence(), meta.lastPacketId(), count,
                    meta.sentUpTo(), meta.earliestMsgExpiryMs());
            store.write(batch -> {
                batch.delete(ENTRIES, entry.key());
                batch.put(METAS, DeviceMsgKeys.metaKey(queueKey), updated.toBytes());
            });
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean markPubRel(String queueKey, int packetId) {
        ReentrantLock lock = lockFor(queueKey);
        lock.lock();
        try {
            Entry entry = find(queueKey, loadMeta(queueKey), packetId);
            if (entry == null) {
                return false;
            }
            store.put(ENTRIES, entry.key(), DeviceMsgEntryCodec.withPacketType(entry.value(), PersistedPacketType.PUBREL));
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeQueue(String queueKey) {
        ReentrantLock lock = lockFor(queueKey);
        lock.lock();
        try {
            store.write(batch -> {
                batch.deleteRange(ENTRIES, DeviceMsgKeys.queuePrefix(queueKey), DeviceMsgKeys.queueEnd(queueKey));
                batch.delete(METAS, DeviceMsgKeys.metaKey(queueKey));
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int moveQueue(String fromQueueKey, String toQueueKey, UnaryOperator<byte[]> bodyMapper) {
        throw new UnsupportedOperationException("moveQueue is not supported yet");
    }

    @Override
    public boolean resetPacketId(String queueKey, int lastPacketId) {
        if (lastPacketId < 0 || lastPacketId > MAX_PACKET_ID) {
            throw new IllegalArgumentException("Packet id " + lastPacketId + " is outside 0..65535");
        }
        ReentrantLock lock = lockFor(queueKey);
        lock.lock();
        try {
            DeviceQueueMeta meta = loadMeta(queueKey);
            if (meta.count() != 0) {
                return false;
            }
            DeviceQueueMeta updated = new DeviceQueueMeta(meta.firstSequence(), meta.lastSequence(), lastPacketId, 0,
                    meta.sentUpTo(), meta.earliestMsgExpiryMs());
            store.put(METAS, DeviceMsgKeys.metaKey(queueKey), updated.toBytes());
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public DeviceQueueInfo info(String queueKey) {
        DeviceQueueMeta meta = loadMeta(queueKey);
        return new DeviceQueueInfo(meta.firstSequence(), meta.lastSequence(), meta.lastPacketId(), meta.count(), meta.sentUpTo());
    }

    @Override
    public int sweepExpired() {
        throw new UnsupportedOperationException("sweepExpired is not supported yet");
    }

    @Override
    public long expiredEntries() {
        return 0;
    }

    private DeviceQueueMeta loadMeta(String queueKey) {
        byte[] bytes = store.get(METAS, DeviceMsgKeys.metaKey(queueKey));
        return bytes == null ? DeviceQueueMeta.empty() : DeviceQueueMeta.fromBytes(bytes);
    }

    /**
     * @return the live entry that carries {@code packetId}, or {@code null}
     */
    private Entry find(String queueKey, DeviceQueueMeta meta, int packetId) {
        if (meta.count() == 0 || packetId < 1 || packetId > MAX_PACKET_ID) {
            return null;
        }
        long sequence = meta.lastSequence() - ((meta.lastPacketId() - packetId + MAX_PACKET_ID) % MAX_PACKET_ID);
        if (sequence < meta.firstSequence()) {
            return null;
        }
        byte[] key = DeviceMsgKeys.entryKey(queueKey, sequence);
        byte[] value = store.get(ENTRIES, key);
        if (value == null || DeviceMsgEntryCodec.header(value).packetId() != packetId) {
            return null;
        }
        return new Entry(sequence, key, value);
    }

    private long firstLiveSequence(String queueKey, long fromSequence, long lastSequence) {
        Long first = store.scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, fromSequence),
                DeviceMsgKeys.entryKey(queueKey, lastSequence + 1),
                iterator -> iterator.isValid() ? DeviceMsgKeys.sequenceOf(iterator.key()) : null);
        if (first == null) {
            throw new StoreException("The device queue " + queueKey + " has no entry in " + fromSequence + ".."
                    + lastSequence + " although its meta counts one");
        }
        return first;
    }

    private static boolean isDense(DeviceQueueMeta meta) {
        return meta.count() == meta.lastSequence() - meta.firstSequence() + 1;
    }

    private static int nextPacketId(int packetId) {
        return packetId % MAX_PACKET_ID + 1;
    }

    private static int stripe(String queueKey) {
        int hash = queueKey.hashCode();
        return (hash ^ (hash >>> 16)) & (LOCK_STRIPES - 1);
    }

    private ReentrantLock lockFor(String queueKey) {
        return locks[stripe(queueKey)];
    }

    private void lockAll(int[] ascendingStripes) {
        for (int stripe : ascendingStripes) {
            locks[stripe].lock();
        }
    }

    private void unlockAll(int[] ascendingStripes) {
        for (int i = ascendingStripes.length - 1; i >= 0; i--) {
            locks[ascendingStripes[i]].unlock();
        }
    }

    private record Entry(long sequence, byte[] key, byte[] value) {
    }

    /**
     * The messages one {@link #append} call adds to one queue, and the trim that keeps the queue within the limit.
     * The new entries are not in the store until the batch is written, so the trim drops them by position.
     */
    private final class PendingAppend {

        private final String queueKey;
        private final DeviceQueueMeta before;
        private final List<byte[]> entries = new ArrayList<>();
        private long lastSequence;
        private int lastPacketId;

        private final List<Long> droppedOld = new ArrayList<>();
        private int droppedNew;
        private long firstSequence;

        PendingAppend(String queueKey, DeviceQueueMeta before) {
            this.queueKey = queueKey;
            this.before = before;
            this.lastSequence = before.lastSequence();
            this.lastPacketId = before.lastPacketId();
        }

        AppendResult add(List<NewDeviceMsg> messages, long now) {
            AppendResult result = new AppendResult(queueKey, lastPacketId, lastSequence + 1, messages.size());
            for (NewDeviceMsg msg : messages) {
                lastSequence++;
                lastPacketId = nextPacketId(lastPacketId);
                entries.add(DeviceMsgEntryCodec.encode(lastPacketId, PersistedPacketType.PUBLISH, msg.createdTimeMs(),
                        msgExpiresAtMs(msg), now + ttlMs, msg.body()));
            }
            return result;
        }

        boolean hasEntries() {
            return !entries.isEmpty();
        }

        /**
         * Drops the oldest entries until the queue fits the limit. An entry older than the last 65,535 sequences is
         * dropped too: its packet id is taken again by a newer entry. Only a queue with holes can hold one, since
         * the limit is at most 65,535.
         */
        void planTrim() {
            long firstNewSequence = before.lastSequence() + 1;
            long oldestInIdWindow = lastSequence - MAX_PACKET_ID + 1;
            int excess = Math.max(0, before.count() + entries.size() - limit);
            long nextOldSequence = -1;
            if (before.count() > 0) {
                if (isDense(before)) {
                    int dropOld = Math.min(excess, before.count());
                    for (int i = 0; i < dropOld; i++) {
                        droppedOld.add(before.firstSequence() + i);
                    }
                    nextOldSequence = dropOld < before.count() ? before.firstSequence() + dropOld : -1;
                } else if (excess > 0 || before.firstSequence() < oldestInIdWindow) {
                    nextOldSequence = scanOldEntries(excess, oldestInIdWindow);
                } else {
                    nextOldSequence = before.firstSequence();
                }
            }
            droppedNew = Math.max(0, excess - droppedOld.size());
            firstSequence = nextOldSequence != -1 ? nextOldSequence : firstNewSequence + droppedNew;
        }

        /**
         * Collects into {@link #droppedOld} the oldest live entries to drop.
         *
         * @return the sequence of the oldest entry kept, or {@code -1} when every old entry is dropped
         */
        private long scanOldEntries(int excess, long oldestInIdWindow) {
            return store.scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, before.firstSequence()),
                    DeviceMsgKeys.entryKey(queueKey, before.lastSequence() + 1), iterator -> {
                        while (iterator.isValid()) {
                            long sequence = DeviceMsgKeys.sequenceOf(iterator.key());
                            if (droppedOld.size() >= excess && sequence >= oldestInIdWindow) {
                                return sequence;
                            }
                            droppedOld.add(sequence);
                            iterator.next();
                        }
                        return -1L;
                    });
        }

        void writeTo(StoreBatch batch) {
            for (long sequence : droppedOld) {
                batch.delete(ENTRIES, DeviceMsgKeys.entryKey(queueKey, sequence));
            }
            long firstNewSequence = before.lastSequence() + 1;
            for (int i = droppedNew; i < entries.size(); i++) {
                batch.put(ENTRIES, DeviceMsgKeys.entryKey(queueKey, firstNewSequence + i), entries.get(i));
            }
            int count = before.count() + entries.size() - droppedOld.size() - droppedNew;
            DeviceQueueMeta after = new DeviceQueueMeta(firstSequence, lastSequence, lastPacketId, count,
                    before.sentUpTo(), before.earliestMsgExpiryMs());
            batch.put(METAS, DeviceMsgKeys.metaKey(queueKey), after.toBytes());
        }

        private static long msgExpiresAtMs(NewDeviceMsg msg) {
            Integer interval = msg.msgExpiryIntervalSec();
            return interval == null ? 0 : msg.createdTimeMs() + interval * 1000L;
        }

    }

}
