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
package org.thingsboard.mqtt.broker.dao.rocksdb.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.client.generic.GenericClientSessionCtxDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.StorageObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The QoS 2 session contexts of the standalone edition, in {@code session_ctx}: the key is the UTF-8 client id and the
 * value is the {@link StorageObjectMapper} form of the context. A write is {@link StoreWriteClass#UPDATE}: a context
 * is small, one per persistent session, and is removed with its session, so a full disk must not refuse the disconnect
 * that stores it. A removal is {@link StoreWriteClass#DELETE}.
 */
@Component
@StandaloneComponent
public class RocksDbGenericClientSessionCtxDao implements GenericClientSessionCtxDao {

    private static final StoreColumnFamily FAMILY = StoreColumnFamily.SESSION_CTX;

    private final RocksDbStore store;

    public RocksDbGenericClientSessionCtxDao(RocksDbStore store) {
        this.store = store;
    }

    @Override
    public GenericClientSessionCtx save(GenericClientSessionCtx genericClientSessionCtx) {
        store.put(StoreWriteClass.UPDATE, FAMILY, key(genericClientSessionCtx), StorageObjectMapper.write(genericClientSessionCtx));
        return genericClientSessionCtx;
    }

    @Override
    public GenericClientSessionCtx findByClientId(String clientId) {
        byte[] value = store.get(FAMILY, key(clientId));
        return value == null ? null : StorageObjectMapper.read(value, GenericClientSessionCtx.class);
    }

    /**
     * @return a complete future; its value is {@code null} when the client has no context, as the SQL DAO answers
     */
    @Override
    public ListenableFuture<GenericClientSessionCtx> findByClientIdAsync(String clientId) {
        try {
            return Futures.immediateFuture(findByClientId(clientId));
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public List<GenericClientSessionCtx> findAll() {
        return store.scan(FAMILY, null, null, iterator -> {
            List<GenericClientSessionCtx> contexts = new ArrayList<>();
            for (; iterator.isValid(); iterator.next()) {
                contexts.add(StorageObjectMapper.read(iterator.value(), GenericClientSessionCtx.class));
            }
            return contexts;
        });
    }

    @Override
    public void remove(String clientId) {
        store.delete(StoreWriteClass.DELETE, FAMILY, key(clientId));
    }

    /**
     * Writes the contexts in one batch.
     */
    @Override
    public void saveAll(Collection<GenericClientSessionCtx> genericClientSessionContexts) {
        if (genericClientSessionContexts.isEmpty()) {
            return;
        }
        store.write(StoreWriteClass.UPDATE, batch -> {
            for (GenericClientSessionCtx ctx : genericClientSessionContexts) {
                batch.put(FAMILY, key(ctx), StorageObjectMapper.write(ctx));
            }
        });
    }

    private static byte[] key(GenericClientSessionCtx ctx) {
        return key(ctx.getClientId());
    }

    private static byte[] key(String clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("A client session context has no client id");
        }
        return clientId.getBytes(StandardCharsets.UTF_8);
    }

}
