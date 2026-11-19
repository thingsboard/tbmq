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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore.ProjectedEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The unauthorized clients of the standalone edition, on the entity store, keyed by client id. A save is written at
 * once, in one batch, and its future is complete when the call returns; it never throws: a failure of the store, a
 * full disk included, is a failed future, so the CONNECT refusal that saved the client still goes out.
 * <p>
 * The store keeps at most {@code standalone.unauthorized-clients.max} clients. A save that adds a client beyond it
 * evicts the oldest by {@code ts} (of equal {@code ts}, the lowest client id first), in the batch of the save; the new
 * client itself is not kept when it is the oldest. A save of a client id already kept replaces it and never evicts.
 * <p>
 * To find the oldest without sorting the projection on every save, the DAO keeps every client id it holds ordered by
 * age, read from the store when it is created and changed by every write of this DAO, under its lock and after the
 * write succeeded. It is the only writer of the type.
 */
@Component
@StandaloneComponent
public class RocksDbUnauthorizedClientDao implements UnauthorizedClientDao {

    static final String MAX_PROPERTY = "standalone.unauthorized-clients.max";

    private static final Comparator<Age> AGE_ORDER = Comparator.comparingLong(Age::ts).thenComparing(Age::clientId);

    private final RocksEntityStore store;
    private final int max;
    private final Object writeLock = new Object();
    // both guarded by writeLock; the same entries
    private final NavigableSet<Age> byAge = new TreeSet<>(AGE_ORDER);
    private final Map<String, Age> ages = new HashMap<>();

    /**
     * @throws IllegalArgumentException when {@code max} is not above 0
     */
    public RocksDbUnauthorizedClientDao(RocksEntityStore store, @Value("${" + MAX_PROPERTY + ":10000}") int max) {
        if (max <= 0) {
            throw new IllegalArgumentException(MAX_PROPERTY + " must be above 0, got " + max);
        }
        this.store = store;
        this.max = max;
        for (UnauthorizedClient client : store.findAll(EntityDescriptors.UNAUTHORIZED_CLIENT)) {
            track(Age.of(client));
        }
    }

    @Override
    public ListenableFuture<Void> save(UnauthorizedClient unauthorizedClient) {
        try {
            synchronized (writeLock) {
                saveLocked(unauthorizedClient);
            }
            return Futures.immediateVoidFuture();
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public ListenableFuture<Void> remove(UnauthorizedClient unauthorizedClient) {
        try {
            deleteById(unauthorizedClient.getClientId());
            return Futures.immediateVoidFuture();
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public void deleteById(String clientId) {
        synchronized (writeLock) {
            if (store.removeById(EntityDescriptors.UNAUTHORIZED_CLIENT, clientId)) {
                untrack(clientId);
            }
        }
    }

    @Override
    public UnauthorizedClient findByClientId(String clientId) {
        return store.findById(EntityDescriptors.UNAUTHORIZED_CLIENT, clientId);
    }

    @Override
    public PageData<UnauthorizedClient> findAll(PageLink pageLink) {
        return store.findPage(EntityDescriptors.UNAUTHORIZED_CLIENT, pageLink, null);
    }

    /**
     * Filters as the SQL query of {@code UnauthorizedClientRepository.findAllV2} does, see {@link QueryFilter}.
     */
    @Override
    public PageData<UnauthorizedClient> findAll(UnauthorizedClientQuery query) {
        return store.findPage(EntityDescriptors.UNAUTHORIZED_CLIENT, query.getPageLink(), new QueryFilter(query));
    }

    /**
     * Removes, in one batch, the clients whose {@code ts} is before now minus {@code ttl} seconds, as the SQL DAO does.
     */
    @Override
    public void cleanupUnauthorizedClients(long ttl) {
        long expirationTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl);
        synchronized (writeLock) {
            // "" is the lowest client id, so this is every client with a ts below the expiration time
            removeAll(new ArrayList<>(byAge.headSet(new Age(expirationTime, ""), false)));
        }
    }

    @Override
    public void deleteAll() {
        synchronized (writeLock) {
            removeAll(new ArrayList<>(byAge));
        }
    }

    private void saveLocked(UnauthorizedClient client) {
        if (client.getClientId() == null) {
            throw new IllegalArgumentException("Cannot save an unauthorized client without a client id");
        }
        Age age = Age.of(client);
        Age old = ages.get(client.getClientId());
        int excess = ages.size() + 1 - max;
        if (old != null || excess <= 0) {
            store.save(EntityDescriptors.UNAUTHORIZED_CLIENT, client);
            if (old != null) {
                byAge.remove(old);
            }
            track(age);
            return;
        }
        // the oldest `excess` of the kept clients and the new one; excess <= ages.size(), so the kept ones never run out
        List<Age> evicted = new ArrayList<>(excess);
        boolean newOneEvicted = false;
        Iterator<Age> kept = byAge.iterator();
        Age next = kept.next();
        for (int i = 0; i < excess; i++) {
            if (!newOneEvicted && (next == null || AGE_ORDER.compare(age, next) < 0)) {
                newOneEvicted = true;
            } else {
                evicted.add(next);
                next = kept.hasNext() ? kept.next() : null;
            }
        }
        if (newOneEvicted) {
            removeAll(evicted);
            return;
        }
        store.removeAndSave(EntityDescriptors.UNAUTHORIZED_CLIENT, clientIds(evicted), client);
        evicted.forEach(e -> untrack(e.clientId()));
        track(age);
    }

    /**
     * Removes the clients in one batch, under the write lock.
     */
    private void removeAll(List<Age> removed) {
        if (removed.isEmpty()) {
            return;
        }
        store.removeAllById(EntityDescriptors.UNAUTHORIZED_CLIENT, clientIds(removed));
        removed.forEach(e -> untrack(e.clientId()));
    }

    private void track(Age age) {
        byAge.add(age);
        ages.put(age.clientId(), age);
    }

    private void untrack(String clientId) {
        Age age = ages.remove(clientId);
        if (age != null) {
            byAge.remove(age);
        }
    }

    private static List<String> clientIds(List<Age> ages) {
        List<String> clientIds = new ArrayList<>(ages.size());
        ages.forEach(age -> clientIds.add(age.clientId()));
        return clientIds;
    }

    /**
     * A kept client's place in the eviction order. A client without a {@code ts}, which the service refuses, is the
     * oldest.
     */
    private record Age(long ts, String clientId) {

        static Age of(UnauthorizedClient client) {
            return new Age(client.getTs() == null ? Long.MIN_VALUE : client.getTs(), client.getClientId());
        }

    }

    /**
     * The filter of {@code UnauthorizedClientRepository.findAllV2}, whose {@code LOWER(..) LIKE '%..%'} is a
     * case-insensitive contains here, with no special characters. A missing text filter is an empty one, which still
     * leaves out a client whose field is {@code null}, as {@code NULL LIKE '%%'} does; the username alone is not
     * filtered then. A {@code null} or empty boolean list is no restriction; a time bound leaves out a client without a
     * {@code ts}. The page link's text search is the store's.
     */
    private static final class QueryFilter implements Predicate<ProjectedEntity> {

        private final String clientId;
        private final String ipAddress;
        private final String username;
        private final String reason;
        private final Long startTime;
        private final Long endTime;
        private final List<Boolean> passwordProvided;
        private final List<Boolean> tlsUsed;

        QueryFilter(UnauthorizedClientQuery query) {
            this.clientId = lowerCase(query.getClientId());
            this.ipAddress = lowerCase(query.getIpAddress());
            this.username = lowerCase(query.getUsername());
            this.reason = lowerCase(query.getReason());
            TimePageLink pageLink = query.getPageLink();
            this.startTime = pageLink.getStartTime();
            this.endTime = pageLink.getEndTime();
            this.passwordProvided = emptyToNull(query.getPasswordProvidedList());
            this.tlsUsed = emptyToNull(query.getTlsUsedList());
        }

        @Override
        public boolean test(ProjectedEntity entity) {
            Map<String, Object> values = entity.filterValues();
            if (!contains(values.get("clientId"), clientId) || !contains(values.get("ipAddress"), ipAddress)
                    || !contains(values.get("reason"), reason)) {
                return false;
            }
            if (!username.isEmpty() && !contains(values.get("username"), username)) {
                return false;
            }
            Object ts = values.get("ts");
            if (startTime != null && !(ts instanceof Long time && time >= startTime)) {
                return false;
            }
            if (endTime != null && !(ts instanceof Long time && time <= endTime)) {
                return false;
            }
            if (passwordProvided != null && !passwordProvided.contains(values.get("passwordProvided"))) {
                return false;
            }
            return tlsUsed == null || tlsUsed.contains(values.get("tlsUsed"));
        }

        private static boolean contains(Object value, String lowerCaseSearch) {
            return value instanceof String text && text.toLowerCase(Locale.ROOT).contains(lowerCaseSearch);
        }

        private static String lowerCase(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }

        private static List<Boolean> emptyToNull(List<Boolean> values) {
            return values == null || values.isEmpty() ? null : values;
        }

    }

}
