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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.exception.EntityConstraintViolationException;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreBatch;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The generic entity DAO of the standalone edition: documents in the {@code entity} column family, their index
 * entries in {@code entity_index}, keyed as {@link EntityKeys} lays out. The value of an index entry is the id bytes.
 * A document and its index entries are always written in one batch.
 * <p>
 * Every write runs under one {@link ReentrantLock} per {@link StoredEntityType}, so the unique checks and the write
 * cannot interleave with another write of the same type. A call that needs several types (a removal that cascades)
 * takes all of their locks up front, in ascending {@link StoredEntityType} order (USER, then WEBSOCKET_CONNECTION,
 * then WEBSOCKET_SUBSCRIPTION), and every other call takes one lock, so no two calls wait for each other in a cycle.
 * Reads take no lock: each batch is atomic, and a document found through an index is returned only if it still has
 * the indexed value.
 */
@Component
@StandaloneComponent
public class RocksEntityStore {

    private final RocksDbStore store;
    private final Map<StoredEntityType, ReentrantLock> locks = new EnumMap<>(StoredEntityType.class);

    public RocksEntityStore(RocksDbStore store) {
        this.store = store;
        for (StoredEntityType type : StoredEntityType.values()) {
            locks.put(type, new ReentrantLock());
        }
    }

    /**
     * Inserts or replaces the document with the entity's id. A UUID type saved without an id gets a random one and the
     * current time as its created time. The argument is not modified; the result is the stored copy. Refused with
     * {@link EntityConstraintViolationException} when a unique value belongs to another document, and then nothing is
     * written.
     */
    public <T, ID> T save(EntityDescriptor<T, ID> d, T entity) {
        return save(d, null, entity);
    }

    /**
     * {@link #removeById} of {@code removedId}, cascades included, and {@link #save} of {@code saved} in one batch.
     * The unique values of the removed documents are free for {@code saved}.
     */
    public <T, ID> T removeAndSave(EntityDescriptor<T, ID> d, ID removedId, T saved) {
        return save(d, Objects.requireNonNull(removedId, "removedId"), saved);
    }

    /**
     * Removes the document, its index entries and, through the cascades of the descriptor, its children, in one batch.
     *
     * @return {@code false} when there was no such document
     */
    public <T, ID> boolean removeById(EntityDescriptor<T, ID> d, ID id) {
        return withLocks(lockedTypes(d), () -> {
            Changes changes = new Changes();
            if (!collectRemoval(d, d.idBytes(id), changes)) {
                return false;
            }
            store.write(StoreWriteClass.DELETE, changes::applyTo);
            return true;
        });
    }

    public <T, ID> T findById(EntityDescriptor<T, ID> d, ID id) {
        return findDocument(d, d.idBytes(id));
    }

    public <T, ID> List<T> findAll(EntityDescriptor<T, ID> d) {
        List<Entry> entries = scan(StoreColumnFamily.ENTITY, EntityKeys.typeLower(d.type()), EntityKeys.typeUpper(d.type()));
        List<T> documents = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            documents.add(decode(d, Arrays.copyOfRange(entry.key(), 1, entry.key().length), entry.value()));
        }
        return documents;
    }

    /**
     * The document whose value of the unique {@code index} is {@code value}, or {@code null}.
     */
    public <T, ID> T findByUnique(EntityDescriptor<T, ID> d, EntityIndex index, String value) {
        Function<T, String> indexValue = requireIndex(d, index);
        if (!isUnique(index)) {
            throw new IllegalArgumentException("The index " + index + " is not unique");
        }
        if (value == null) {
            return null;
        }
        byte[] id = store.get(StoreColumnFamily.ENTITY_INDEX, EntityKeys.uniqueIndexKey(index, value));
        return id == null ? null : findIfIndexed(d, id, indexValue, value);
    }

    /**
     * Every document whose value of {@code index}, unique or not, is {@code value}, in id order.
     */
    public <T, ID> List<T> findByIndex(EntityDescriptor<T, ID> d, EntityIndex index, String value) {
        Function<T, String> indexValue = requireIndex(d, index);
        if (value == null) {
            return List.of();
        }
        List<T> documents = new ArrayList<>();
        for (Entry entry : indexEntries(index, value)) {
            T document = findIfIndexed(d, entry.value(), indexValue, value);
            if (document != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    public <T, ID> boolean existsByIndex(EntityDescriptor<T, ID> d, EntityIndex index, String value) {
        requireIndex(d, index);
        if (value == null) {
            return false;
        }
        if (isUnique(index)) {
            return store.get(StoreColumnFamily.ENTITY_INDEX, EntityKeys.uniqueIndexKey(index, value)) != null;
        }
        return store.scan(StoreColumnFamily.ENTITY_INDEX, EntityKeys.nonUniqueLower(index, value),
                EntityKeys.nonUniqueUpper(index, value), it -> it.isValid());
    }

    private <T, ID> T save(EntityDescriptor<T, ID> d, ID removedId, T entity) {
        // a copy, so the argument is not modified and the caller cannot change the result through it
        T document = StorageObjectMapper.read(StorageObjectMapper.write(entity), d.domainClass());
        if (d.id().apply(document) == null) {
            if (d.idAssigner() == null) {
                throw new IllegalArgumentException("Cannot save a " + d.type() + " without an id");
            }
            d.idAssigner().accept(document, UUID.randomUUID());
            d.createdTimeSetter().accept(document, System.currentTimeMillis());
        }
        byte[] id = d.idBytes(d.id().apply(document));
        byte[] value = StorageObjectMapper.write(document);
        Map<EntityIndex, String> newValues = indexValues(d, document);
        EnumSet<StoredEntityType> lockedTypes = removedId == null ? EnumSet.of(d.type()) : lockedTypes(d);
        withLocks(lockedTypes, () -> {
            Changes changes = new Changes();
            if (removedId != null) {
                collectRemoval(d, d.idBytes(removedId), changes);
            }
            byte[] key = EntityKeys.entityKey(d.type(), id);
            Map<EntityIndex, String> oldValues = Map.of();
            if (!changes.removes(key)) {
                T old = findDocument(d, id);
                if (old != null) {
                    oldValues = indexValues(d, old);
                }
            }
            checkUnique(d, id, newValues, changes);
            for (Map.Entry<EntityIndex, String> old : oldValues.entrySet()) {
                if (!old.getValue().equals(newValues.get(old.getKey()))) {
                    changes.delete(StoreColumnFamily.ENTITY_INDEX, indexKey(old.getKey(), old.getValue(), id));
                }
            }
            changes.put(StoreColumnFamily.ENTITY, key, value);
            newValues.forEach((index, indexed) -> changes.put(StoreColumnFamily.ENTITY_INDEX, indexKey(index, indexed, id), id));
            // new data even for an update: a management create or update is refused below the free-space watermark
            store.write(StoreWriteClass.NEW_DATA, changes::applyTo);
            return null;
        });
        return document;
    }

    private <T> void checkUnique(EntityDescriptor<T, ?> d, byte[] id, Map<EntityIndex, String> values, Changes changes) {
        for (Map.Entry<EntityIndex, String> entry : values.entrySet()) {
            EntityIndex index = entry.getKey();
            if (!isUnique(index)) {
                continue;
            }
            byte[] owner = store.get(StoreColumnFamily.ENTITY_INDEX, EntityKeys.uniqueIndexKey(index, entry.getValue()));
            if (owner != null && !Arrays.equals(owner, id) && !changes.removes(EntityKeys.entityKey(d.type(), owner))) {
                throw new EntityConstraintViolationException(index.constraintName(), null);
            }
        }
    }

    /**
     * Adds the deletes of the document, its index entries and its cascaded children to {@code changes}.
     *
     * @return {@code false} when there is no such document
     */
    private <T> boolean collectRemoval(EntityDescriptor<T, ?> d, byte[] id, Changes changes) {
        byte[] key = EntityKeys.entityKey(d.type(), id);
        if (changes.removes(key)) {
            return true;
        }
        T document = findDocument(d, id);
        if (document == null) {
            return false;
        }
        changes.remove(key);
        indexValues(d, document).forEach((index, value) -> changes.delete(StoreColumnFamily.ENTITY_INDEX, indexKey(index, value, id)));
        String idText = EntityKeys.idText(d.type(), id);
        for (EntityDescriptor.Cascade cascade : d.cascades()) {
            for (Entry child : indexEntries(cascade.childIndex(), idText)) {
                if (!collectRemoval(cascade.child(), child.value(), changes)) {
                    // an entry whose document is gone: removed with its parent all the same
                    changes.delete(StoreColumnFamily.ENTITY_INDEX, child.key());
                }
            }
        }
        return true;
    }

    /**
     * The entries of {@code value} in {@code index}; the value of each is the id of its document.
     */
    private List<Entry> indexEntries(EntityIndex index, String value) {
        if (isUnique(index)) {
            byte[] key = EntityKeys.uniqueIndexKey(index, value);
            byte[] id = store.get(StoreColumnFamily.ENTITY_INDEX, key);
            return id == null ? List.of() : List.of(new Entry(key, id));
        }
        return scan(StoreColumnFamily.ENTITY_INDEX, EntityKeys.nonUniqueLower(index, value), EntityKeys.nonUniqueUpper(index, value));
    }

    private List<Entry> scan(StoreColumnFamily cf, byte[] lowerInclusive, byte[] upperExclusive) {
        return store.scan(cf, lowerInclusive, upperExclusive, it -> {
            List<Entry> entries = new ArrayList<>();
            for (; it.isValid(); it.next()) {
                entries.add(new Entry(it.key(), it.value()));
            }
            return entries;
        });
    }

    private <T> T findIfIndexed(EntityDescriptor<T, ?> d, byte[] id, Function<T, String> indexValue, String value) {
        T document = findDocument(d, id);
        // the document can change between the index read and its own read
        return document != null && value.equals(indexValue.apply(document)) ? document : null;
    }

    private <T> T findDocument(EntityDescriptor<T, ?> d, byte[] id) {
        byte[] value = store.get(StoreColumnFamily.ENTITY, EntityKeys.entityKey(d.type(), id));
        return value == null ? null : decode(d, id, value);
    }

    private static <T> T decode(EntityDescriptor<T, ?> d, byte[] id, byte[] value) {
        try {
            return StorageObjectMapper.read(value, d.domainClass());
        } catch (StoreException e) {
            throw new StoreException("Cannot read the " + d.type() + " " + EntityKeys.idText(d.type(), id) + ": " + e.getMessage(), e);
        }
    }

    private static <T> Map<EntityIndex, String> indexValues(EntityDescriptor<T, ?> d, T document) {
        Map<EntityIndex, String> values = new EnumMap<>(EntityIndex.class);
        d.indexes().forEach((index, value) -> {
            String indexed = value.apply(document);
            if (indexed != null) {
                values.put(index, indexed);
            }
        });
        return values;
    }

    private static <T> Function<T, String> requireIndex(EntityDescriptor<T, ?> d, EntityIndex index) {
        Function<T, String> value = d.indexes().get(index);
        if (value == null) {
            throw new IllegalArgumentException(d.type() + " has no index " + index);
        }
        return value;
    }

    // an index of an entity type is unique exactly when it has the name of its SQL constraint
    private static boolean isUnique(EntityIndex index) {
        return index.constraintName() != null;
    }

    private static byte[] indexKey(EntityIndex index, String value, byte[] id) {
        return isUnique(index) ? EntityKeys.uniqueIndexKey(index, value) : EntityKeys.nonUniqueIndexKey(index, value, id);
    }

    /**
     * The type of the descriptor and every type its cascades reach, in lock order.
     */
    private static EnumSet<StoredEntityType> lockedTypes(EntityDescriptor<?, ?> d) {
        EnumSet<StoredEntityType> types = EnumSet.of(d.type());
        for (EntityDescriptor.Cascade cascade : d.cascades()) {
            types.addAll(lockedTypes(cascade.child()));
        }
        return types;
    }

    private <R> R withLocks(EnumSet<StoredEntityType> types, Supplier<R> body) {
        // an EnumSet iterates in ordinal order, which is the lock order
        List<ReentrantLock> taken = new ArrayList<>(types.size());
        try {
            for (StoredEntityType type : types) {
                ReentrantLock lock = locks.get(type);
                lock.lock();
                taken.add(lock);
            }
            return body.get();
        } finally {
            for (int i = taken.size() - 1; i >= 0; i--) {
                taken.get(i).unlock();
            }
        }
    }

    private record Entry(byte[] key, byte[] value) {
    }

    // a null value is a delete
    private record Write(StoreColumnFamily cf, byte[] key, byte[] value) {
    }

    /**
     * The puts and deletes of one batch, in order, and the entity keys it removes.
     */
    private static final class Changes {

        private final List<Write> writes = new ArrayList<>();
        private final Set<ByteBuffer> removedKeys = new HashSet<>();

        void put(StoreColumnFamily cf, byte[] key, byte[] value) {
            writes.add(new Write(cf, key, value));
        }

        void delete(StoreColumnFamily cf, byte[] key) {
            writes.add(new Write(cf, key, null));
        }

        void remove(byte[] entityKey) {
            removedKeys.add(ByteBuffer.wrap(entityKey));
            delete(StoreColumnFamily.ENTITY, entityKey);
        }

        boolean removes(byte[] entityKey) {
            return removedKeys.contains(ByteBuffer.wrap(entityKey));
        }

        void applyTo(StoreBatch batch) {
            for (Write write : writes) {
                if (write.value() == null) {
                    batch.delete(write.cf(), write.key());
                } else {
                    batch.put(write.cf(), write.key(), write.value());
                }
            }
        }

    }

}
