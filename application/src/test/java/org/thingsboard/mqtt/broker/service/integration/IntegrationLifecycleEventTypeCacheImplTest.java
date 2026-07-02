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
package org.thingsboard.mqtt.broker.service.integration;

import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IntegrationLifecycleEventTypeCacheImplTest {

    private IntegrationLifecycleEventTypeCacheImpl cache;

    @Before
    public void setUp() {
        cache = new IntegrationLifecycleEventTypeCacheImpl();
    }

    @Test
    public void givenEmptyCache_whenGetIntegrationIds_thenReturnsEmptyNeverNull() {
        Set<String> ids = cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED);
        assertTrue(ids.isEmpty());
    }

    @Test
    public void givenEmptyCache_whenGetIntegrationIdsTwice_thenSameSharedEmptyInstance() {
        Set<String> first = cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED);
        Set<String> second = cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_DISCONNECTED);
        assertSame(first, second); // allocation-free reads share one empty set
    }

    @Test
    public void givenPut_whenGetIntegrationIds_thenReverseIndexResolvesIds() {
        cache.put("ie-1", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED, ClientLifecycleEventType.CLIENT_SUBSCRIBED));
        cache.put("ie-2", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));

        assertEquals(Set.of("ie-1", "ie-2"), cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED));
        assertEquals(Set.of("ie-1"), cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED));
        assertTrue(cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED).isEmpty());
    }

    @Test
    public void givenPutThenRemove_whenGetIntegrationIds_thenReverseIndexRebuilt() {
        cache.put("ie-1", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));
        cache.put("ie-2", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));
        cache.remove("ie-1");

        assertEquals(Set.of("ie-2"), cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED));
    }

    @Test
    public void givenPutEmpty_whenGetIntegrationIds_thenTreatedAsRemoval() {
        cache.put("ie-1", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));
        cache.put("ie-1", Set.of()); // empty == remove
        assertTrue(cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED).isEmpty());
    }

    @Test
    public void givenResult_whenMutate_thenImmutable() {
        cache.put("ie-1", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));
        Set<String> ids = cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED);
        assertThrows(UnsupportedOperationException.class, () -> ids.add("ie-x"));
    }

    @Test
    public void givenConfigProto_whenProcess_thenReverseIndexResolvesIds() {
        IntegrationLifecycleConfigProto proto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("ie-1")
                .addLifecycleEventTypes("CLIENT_CONNECTED")
                .addLifecycleEventTypes("CLIENT_SUBSCRIBED")
                .build();

        cache.processIntegrationLifecycleConfig(proto);

        assertEquals(Set.of("ie-1"), cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED));
        assertEquals(Set.of("ie-1"), cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED));
    }

    @Test
    public void givenDeletedConfigProto_whenProcess_thenRemoved() {
        cache.put("ie-1", Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));
        IntegrationLifecycleConfigProto proto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("ie-1")
                .setDeleted(true)
                .build();

        cache.processIntegrationLifecycleConfig(proto);

        assertTrue(cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED).isEmpty());
    }

    @Test
    public void givenConfigProtoWithUnknownType_whenProcess_thenKnownTypesApplied() {
        IntegrationLifecycleConfigProto proto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("ie-1")
                .addLifecycleEventTypes("CLIENT_CONNECTED")
                .addLifecycleEventTypes("NOT_A_REAL_TYPE")
                .build();

        cache.processIntegrationLifecycleConfig(proto);

        assertEquals(Set.of("ie-1"), cache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED));
    }

}
