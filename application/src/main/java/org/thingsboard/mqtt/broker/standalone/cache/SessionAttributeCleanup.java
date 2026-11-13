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
package org.thingsboard.mqtt.broker.standalone.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;

import java.util.Set;

/**
 * Removes the session attributes of the clients that have no session after the startup load: a non-persistent
 * session leaves its attributes behind when the process dies. It also removes the attributes stored under a cache
 * name that is not one of {@link StandaloneCacheManager#storeBackedCacheNames}, which a changed
 * {@code cache.cachePrefix} leaves behind, whether or not the client has a session. Runs right after
 * {@code BrokerInitializer} (order 1) and before the MQTT listeners bind, so no client can connect meanwhile.
 */
@Slf4j
@Component
@StandaloneComponent
public class SessionAttributeCleanup {

    private final KeyValueStore sessionAttributes;
    private final ClientSessionCache clientSessionCache;
    private final Set<String> currentCacheNames;

    public SessionAttributeCleanup(BrokerStateStores stores, ClientSessionCache clientSessionCache,
                                   CacheProperties cacheProperties) {
        this.sessionAttributes = stores.clientSessionAttributes();
        this.clientSessionCache = clientSessionCache;
        this.currentCacheNames = StandaloneCacheManager.storeBackedCacheNames(cacheProperties);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void removeAttributesOfClientsWithoutSession() {
        var sessions = clientSessionCache.getAllClientSessions();
        // the column family belongs to the two session attribute caches, so a key of any other shape, or under a cache name
        // that is not a current one (the cache prefix changed), is garbage
        int removed = sessionAttributes.retainKeys(key -> {
            int separator = key.indexOf(StoreBackedCache.SEPARATOR);
            return separator >= 0
                    && currentCacheNames.contains(key.substring(0, separator))
                    && sessions.containsKey(key.substring(separator + 1));
        });
        log.info("Removed {} session attributes of clients without a session", removed);
    }

}
