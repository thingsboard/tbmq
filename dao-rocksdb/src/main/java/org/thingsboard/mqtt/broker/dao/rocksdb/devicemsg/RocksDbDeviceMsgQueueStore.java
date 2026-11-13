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

import org.rocksdb.RocksIterator;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

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
 * <p>
 * An entry is sent when its sequence is at most {@code sentUpTo} or it is a PUBREL. An entry expires when its TTL has
 * passed, or when it is unsent and its message expiry time has passed: a sent entry never expires by its message
 * expiry ([MQTT-4.3.3-7], [MQTT-4.4.0-1]). {@code earliestExpiryMs} of the meta is a lower bound of the TTLs of the
 * entries and of the message expiry times of the unsent ones: an append or a move lowers it, a scan that sees the
 * whole queue recomputes it. TTLs do not grow with the sequence, since a moved entry keeps the TTL it had in its group
 * queue. Expired entries are deleted by {@link #read}, by the trim of a full queue and by {@link #sweepExpired}. The
 * trim and the sweep look for them only once the bound has passed; the trim drops them before any live entry.
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
    private final AtomicLong expiredEntries = new AtomicLong();
    private final AtomicLong iteratorSeeks = new AtomicLong();

    @Autowired
    public RocksDbDeviceMsgQueueStore(RocksDbStore store,
                                      @Value("${mqtt.persistent-session.device.persisted-messages.limit}") int limit,
                                      @Value("${mqtt.persistent-session.device.persisted-messages.ttl}") int ttlSeconds) {
        this(store, limit, ttlSeconds, Clock.systemUTC());
    }

    public RocksDbDeviceMsgQueueStore(RocksDbStore store, int limit, int ttlSeconds, Clock clock) {
        if (limit < 0) {
            throw new IllegalArgumentException("Persisted messages limit can't be negative!");
        }
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
            changed.forEach(queue -> queue.planTrim(now));
            if (!changed.isEmpty()) {
                store.write(batch -> changed.forEach(queue -> queue.writeTo(batch)));
                changed.forEach(queue -> expiredEntries.addAndGet(queue.expired));
            }
            return results;
        } finally {
            unlockAll(stripes);
        }
    }

    @Override
    public List<StoredDeviceMsg> read(String queueKey, long afterSequence, int limit) {
        return read(queueKey, afterSequence, limit, false);
    }

    @Override
    public List<StoredDeviceMsg> readForDelivery(String queueKey, long afterSequence, int limit) {
        return read(queueKey, afterSequence, limit, true);
    }

    /**
     * Deletes the expired entries it meets on the way and does not return them. With {@code markSent} it raises the
     * sent mark to the last entry returned; the deletes and the new mark go in one batch.
     */
    private List<StoredDeviceMsg> read(String queueKey, long afterSequence, int limit, boolean markSent) {
        if (limit <= 0) {
            return List.of();
        }
        ReentrantLock lock = lockFor(queueKey);
        lock.lock();
        try {
            DeviceQueueMeta meta = loadMeta(queueKey);
            if (meta.count() == 0) {
                return List.of();
            }
            long now = clock.millis();
            // starting at the head skips the tombstones of trimmed and acknowledged entries below it
            long from = Math.max(afterSequence, meta.firstSequence() - 1) + 1;
            List<StoredDeviceMsg> page = new ArrayList<>(Math.min(limit, meta.count()));
            List<Long> expired = new ArrayList<>();
            ExpiryBound bound = new ExpiryBound();
            byte[] fromKey = DeviceMsgKeys.entryKey(queueKey, from);
            boolean reachedEnd = scan(ENTRIES, fromKey, DeviceMsgKeys.queueEnd(queueKey), iterator -> {
                while (iterator.isValid() && page.size() < limit) {
                    long sequence = DeviceMsgKeys.sequenceOf(iterator.key());
                    byte[] value = iterator.value();
                    DeviceMsgEntryCodec.Header header = DeviceMsgEntryCodec.header(value);
                    if (isExpired(sequence, header, meta.sentUpTo(), now)) {
                        expired.add(sequence);
                    } else {
                        boolean sent = isSent(sequence, header, meta.sentUpTo());
                        bound.add(sent, header);
                        page.add(new StoredDeviceMsg(sequence, header.packetId(), header.packetType(), sent,
                                header.createdTimeMs(), header.msgExpiresAtMs(), DeviceMsgEntryCodec.body(value)));
                    }
                    iterator.next();
                }
                return !iterator.isValid();
            });
            long sentUpTo = markSent && !page.isEmpty()
                    ? Math.max(meta.sentUpTo(), page.get(page.size() - 1).sequence())
                    : meta.sentUpTo();
            DeviceQueueMeta marked = meta.withSentUpTo(sentUpTo);
            if (!expired.isEmpty()) {
                boolean fromHead = from == meta.firstSequence();
                long first = meta.firstSequence();
                if (fromHead && expired.get(0) == first && expired.size() < meta.count()) {
                    first = page.isEmpty()
                            ? firstLiveSequence(queueKey, expired.get(expired.size() - 1) + 1, meta.lastSequence())
                            : page.get(0).sequence();
                }
                // only a read that saw the whole queue knows every entry; otherwise the old bound still holds
                long earliest = fromHead && reachedEnd ? bound.value() : meta.earliestExpiryMs();
                deleteExpired(queueKey, marked, expired, first, earliest);
            } else if (sentUpTo != meta.sentUpTo()) {
                store.put(METAS, DeviceMsgKeys.metaKey(queueKey), marked.toBytes());
            }
            return page;
        } finally {
            lock.unlock();
        }
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
                    meta.sentUpTo(), count == 0 ? 0 : meta.earliestExpiryMs());
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

    /**
     * Appends every live entry of {@code fromQueueKey}, in order, to {@code toQueueKey} as an unsent PUBLISH with the
     * next sequence and packet id of {@code toQueueKey}, its body passed through {@code bodyMapper} and its created and
     * expiry times kept; the limit trim of {@code toQueueKey} applies. An entry whose message expiry or TTL has passed
     * is dropped and counted as expired, even one already sent from {@code fromQueueKey}: it would reach the member as
     * a new message ([MQTT-3.3.2-5]). {@code fromQueueKey} is left empty and keeps its last sequence and packet id.
     *
     * @return the number of entries appended to {@code toQueueKey}
     */
    @Override
    public int moveQueue(String fromQueueKey, String toQueueKey, UnaryOperator<byte[]> bodyMapper) {
        if (fromQueueKey.equals(toQueueKey)) {
            throw new IllegalArgumentException("The device queue " + fromQueueKey + " can't be moved into itself");
        }
        int[] stripes = IntStream.of(stripe(fromQueueKey), stripe(toQueueKey)).distinct().sorted().toArray();
        lockAll(stripes);
        try {
            DeviceQueueMeta from = loadMeta(fromQueueKey);
            if (from.count() == 0) {
                return 0;
            }
            long now = clock.millis();
            List<byte[]> values = scan(ENTRIES, DeviceMsgKeys.entryKey(fromQueueKey, from.firstSequence()),
                    DeviceMsgKeys.entryKey(fromQueueKey, from.lastSequence() + 1), iterator -> {
                        List<byte[]> all = new ArrayList<>(from.count());
                        while (iterator.isValid()) {
                            all.add(iterator.value());
                            iterator.next();
                        }
                        return all;
                    });
            PendingAppend to = new PendingAppend(toQueueKey, loadMeta(toQueueKey));
            int expired = 0;
            for (byte[] value : values) {
                DeviceMsgEntryCodec.Header header = DeviceMsgEntryCodec.header(value);
                if (ttlPassed(header, now) || msgExpiryPassed(header, now)) {
                    expired++;
                } else {
                    to.add(header.createdTimeMs(), header.msgExpiresAtMs(), header.ttlExpiresAtMs(),
                            bodyMapper.apply(DeviceMsgEntryCodec.body(value)));
                }
            }
            if (to.hasEntries()) {
                to.planTrim(now);
            }
            DeviceQueueMeta emptied = new DeviceQueueMeta(from.lastSequence() + 1, from.lastSequence(),
                    from.lastPacketId(), 0, from.sentUpTo(), 0);
            store.write(batch -> {
                batch.deleteRange(ENTRIES, DeviceMsgKeys.queuePrefix(fromQueueKey),
                        DeviceMsgKeys.queueEnd(fromQueueKey));
                batch.put(METAS, DeviceMsgKeys.metaKey(fromQueueKey), emptied.toBytes());
                if (to.hasEntries()) {
                    to.writeTo(batch);
                }
            });
            expiredEntries.addAndGet(expired + to.expired);
            return to.entries.size();
        } finally {
            unlockAll(stripes);
        }
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
                    meta.sentUpTo(), meta.earliestExpiryMs());
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

    /**
     * Walks the metas and scans, each under its own lock, every queue whose {@code earliestExpiryMs} has passed.
     */
    @Override
    public int sweepExpired() {
        long now = clock.millis();
        List<String> due = scan(METAS, null, null, iterator -> {
            List<String> queueKeys = new ArrayList<>();
            while (iterator.isValid()) {
                String queueKey = new String(iterator.key(), StandardCharsets.UTF_8);
                if (expiryDue(DeviceQueueMeta.fromBytes(iterator.value()), now)) {
                    queueKeys.add(queueKey);
                }
                iterator.next();
            }
            return queueKeys;
        });
        int removed = 0;
        for (String queueKey : due) {
            ReentrantLock lock = lockFor(queueKey);
            lock.lock();
            try {
                DeviceQueueMeta meta = loadMeta(queueKey);
                if (expiryDue(meta, now)) {
                    removed += expireQueue(queueKey, meta, now);
                }
            } finally {
                lock.unlock();
            }
        }
        return removed;
    }

    @Override
    public long expiredEntries() {
        return expiredEntries.get();
    }

    /**
     * Number of iterators this store opened; lets tests prove that a path does not scan.
     */
    long iteratorSeekCount() {
        return iteratorSeeks.get();
    }

    private <T> T scan(StoreColumnFamily cf, byte[] lowerInclusive, byte[] upperExclusive,
                       Function<RocksIterator, T> reader) {
        iteratorSeeks.incrementAndGet();
        return store.scan(cf, lowerInclusive, upperExclusive, reader);
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
        Long first = scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, fromSequence),
                DeviceMsgKeys.entryKey(queueKey, lastSequence + 1),
                iterator -> iterator.isValid() ? DeviceMsgKeys.sequenceOf(iterator.key()) : null);
        if (first == null) {
            throw new StoreException("The device queue " + queueKey + " has no entry in " + fromSequence + ".."
                    + lastSequence + " although its meta counts one");
        }
        return first;
    }

    /**
     * Whether the queue may hold an expired entry: its expiry bound has passed.
     */
    private static boolean expiryDue(DeviceQueueMeta meta, long now) {
        return meta.count() > 0 && meta.earliestExpiryMs() != 0 && meta.earliestExpiryMs() <= now;
    }

    /**
     * Deletes every expired entry of the queue and recomputes its expiry bound. Runs under the queue's lock.
     *
     * @return the number of entries deleted
     */
    private int expireQueue(String queueKey, DeviceQueueMeta meta, long now) {
        List<Long> expired = new ArrayList<>();
        ExpiryBound bound = new ExpiryBound();
        long firstLive = scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, meta.firstSequence()),
                DeviceMsgKeys.entryKey(queueKey, meta.lastSequence() + 1), iterator -> {
                    long first = -1;
                    while (iterator.isValid()) {
                        long sequence = DeviceMsgKeys.sequenceOf(iterator.key());
                        DeviceMsgEntryCodec.Header header = DeviceMsgEntryCodec.header(iterator.value());
                        if (isExpired(sequence, header, meta.sentUpTo(), now)) {
                            expired.add(sequence);
                        } else {
                            bound.add(isSent(sequence, header, meta.sentUpTo()), header);
                            first = first == -1 ? sequence : first;
                        }
                        iterator.next();
                    }
                    return first;
                });
        if (!expired.isEmpty() || bound.value() != meta.earliestExpiryMs()) {
            deleteExpired(queueKey, meta, expired, firstLive, bound.value());
        }
        return expired.size();
    }

    /**
     * Deletes the given expired entries and writes the meta with the new head and expiry bound in one batch.
     */
    private void deleteExpired(String queueKey, DeviceQueueMeta meta, List<Long> expired, long firstSequence,
                               long earliestExpiryMs) {
        int count = meta.count() - expired.size();
        DeviceQueueMeta updated = new DeviceQueueMeta(count == 0 ? meta.lastSequence() + 1 : firstSequence,
                meta.lastSequence(), meta.lastPacketId(), count, meta.sentUpTo(), count == 0 ? 0 : earliestExpiryMs);
        store.write(batch -> {
            for (long sequence : expired) {
                batch.delete(ENTRIES, DeviceMsgKeys.entryKey(queueKey, sequence));
            }
            batch.put(METAS, DeviceMsgKeys.metaKey(queueKey), updated.toBytes());
        });
        expiredEntries.addAndGet(expired.size());
    }

    private static boolean isSent(long sequence, DeviceMsgEntryCodec.Header header, long sentUpTo) {
        return sequence <= sentUpTo || header.packetType() == PersistedPacketType.PUBREL;
    }

    private static boolean isExpired(long sequence, DeviceMsgEntryCodec.Header header, long sentUpTo, long now) {
        return ttlPassed(header, now) || !isSent(sequence, header, sentUpTo) && msgExpiryPassed(header, now);
    }

    private static boolean msgExpiryPassed(DeviceMsgEntryCodec.Header header, long now) {
        return header.msgExpiresAtMs() != 0 && header.msgExpiresAtMs() <= now;
    }

    private static boolean ttlPassed(DeviceMsgEntryCodec.Header header, long now) {
        return header.ttlExpiresAtMs() != 0 && header.ttlExpiresAtMs() <= now;
    }

    /**
     * @return when the entry expires: its TTL, or its message expiry time when that is earlier and it is unsent;
     * {@code 0} meaning never
     */
    private static long expiresAtMs(boolean sent, DeviceMsgEntryCodec.Header header) {
        return sent ? header.ttlExpiresAtMs() : earlier(header.ttlExpiresAtMs(), header.msgExpiresAtMs());
    }

    /**
     * @return the earlier of two expiry times, {@code 0} meaning none
     */
    private static long earlier(long a, long b) {
        return a == 0 ? b : b == 0 ? a : Math.min(a, b);
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
     * Collects the earliest expiry time of the entries a scan keeps.
     */
    private static final class ExpiryBound {

        private long value;

        void add(boolean sent, DeviceMsgEntryCodec.Header header) {
            value = earlier(value, expiresAtMs(sent, header));
        }

        long value() {
            return value;
        }

    }

    /**
     * The messages one {@link #append} or {@link #moveQueue} call adds to one queue, and the trim that keeps the queue
     * within the limit. The new entries are not in the store until the batch is written, so the trim drops them by
     * position.
     */
    private final class PendingAppend {

        private final String queueKey;
        private final DeviceQueueMeta before;
        private final List<byte[]> entries = new ArrayList<>();
        private long lastSequence;
        private int lastPacketId;

        private final List<Long> droppedOld = new ArrayList<>();
        private int droppedNew;
        private int expired;
        private long firstSequence;
        private long oldEarliestExpiryMs;
        private long newEarliestExpiryMs;

        PendingAppend(String queueKey, DeviceQueueMeta before) {
            this.queueKey = queueKey;
            this.before = before;
            this.lastSequence = before.lastSequence();
            this.lastPacketId = before.lastPacketId();
            this.oldEarliestExpiryMs = before.earliestExpiryMs();
        }

        AppendResult add(List<NewDeviceMsg> messages, long now) {
            AppendResult result = new AppendResult(queueKey, lastPacketId, lastSequence + 1, messages.size());
            for (NewDeviceMsg msg : messages) {
                add(msg.createdTimeMs(), msgExpiresAtMs(msg), now + ttlMs, msg.body());
            }
            return result;
        }

        /**
         * Adds one unsent PUBLISH entry with the next sequence and packet id of the queue.
         */
        void add(long createdTimeMs, long msgExpiresAtMs, long ttlExpiresAtMs, byte[] body) {
            lastSequence++;
            lastPacketId = nextPacketId(lastPacketId);
            newEarliestExpiryMs = earlier(newEarliestExpiryMs, earlier(ttlExpiresAtMs, msgExpiresAtMs));
            entries.add(DeviceMsgEntryCodec.encode(lastPacketId, PersistedPacketType.PUBLISH, createdTimeMs,
                    msgExpiresAtMs, ttlExpiresAtMs, body));
        }

        boolean hasEntries() {
            return !entries.isEmpty();
        }

        /**
         * Drops the oldest entries until the queue fits the limit. An entry older than the last 65,535 sequences is
         * dropped too: its packet id is taken again by a newer entry. Only a queue with holes can hold one, since
         * the limit is at most 65,535. When the queue is over the limit and may hold expired entries, those go first.
         */
        void planTrim(long now) {
            long firstNewSequence = before.lastSequence() + 1;
            long oldestInIdWindow = lastSequence - MAX_PACKET_ID + 1;
            int excess = Math.max(0, before.count() + entries.size() - limit);
            long nextOldSequence = -1;
            if (before.count() > 0) {
                if (excess > 0 && expiryDue(before, now)) {
                    nextOldSequence = scanOldEntriesExpiredFirst(excess, oldestInIdWindow, now);
                } else if (isDense(before)) {
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
            return scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, before.firstSequence()),
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

        /**
         * Collects into {@link #droppedOld} every expired old entry, then the oldest live ones while the queue is still
         * over the limit, and recomputes the expiry bound of the old entries kept.
         *
         * @return the sequence of the oldest entry kept, or {@code -1} when every old entry is dropped
         */
        private long scanOldEntriesExpiredFirst(int excess, long oldestInIdWindow, long now) {
            List<Long> live = new ArrayList<>();
            List<Long> liveExpiry = new ArrayList<>();
            scan(ENTRIES, DeviceMsgKeys.entryKey(queueKey, before.firstSequence()),
                    DeviceMsgKeys.entryKey(queueKey, before.lastSequence() + 1), iterator -> {
                        while (iterator.isValid()) {
                            long sequence = DeviceMsgKeys.sequenceOf(iterator.key());
                            DeviceMsgEntryCodec.Header header = DeviceMsgEntryCodec.header(iterator.value());
                            if (isExpired(sequence, header, before.sentUpTo(), now)) {
                                droppedOld.add(sequence);
                            } else {
                                live.add(sequence);
                                liveExpiry.add(expiresAtMs(isSent(sequence, header, before.sentUpTo()), header));
                            }
                            iterator.next();
                        }
                        return null;
                    });
            expired = droppedOld.size();
            int kept = 0;
            while (kept < live.size() && (droppedOld.size() < excess || live.get(kept) < oldestInIdWindow)) {
                droppedOld.add(live.get(kept++));
            }
            oldEarliestExpiryMs = 0;
            for (int i = kept; i < live.size(); i++) {
                oldEarliestExpiryMs = earlier(oldEarliestExpiryMs, liveExpiry.get(i));
            }
            return kept < live.size() ? live.get(kept) : -1;
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
                    before.sentUpTo(), earlier(oldEarliestExpiryMs, newEarliestExpiryMs));
            batch.put(METAS, DeviceMsgKeys.metaKey(queueKey), after.toBytes());
        }

        /**
         * An interval of 0 is kept for one second: such a copy is appended only for a subscriber that is online, which
         * picks it up at once.
         */
        private static long msgExpiresAtMs(NewDeviceMsg msg) {
            Integer interval = msg.msgExpiryIntervalSec();
            if (interval == null) {
                return 0;
            }
            return msg.createdTimeMs() + (interval == 0 ? 1000L : interval * 1000L);
        }

    }

}
