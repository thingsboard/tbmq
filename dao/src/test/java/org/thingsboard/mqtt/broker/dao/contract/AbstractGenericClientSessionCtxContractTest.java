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
package org.thingsboard.mqtt.broker.dao.contract;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.dao.client.generic.GenericClientSessionCtxDao;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractGenericClientSessionCtxContractTest extends AbstractContractTest {

    @Autowired
    protected GenericClientSessionCtxService genericClientSessionCtxService;

    @Autowired
    protected GenericClientSessionCtxDao genericClientSessionCtxDao;

    private final List<String> createdClientIds = new ArrayList<>();

    @After
    public void removeCreatedContexts() {
        createdClientIds.forEach(genericClientSessionCtxDao::remove);
        createdClientIds.clear();
    }

    @Test
    public void givenSavedContext_whenFind_thenReturnsIt() {
        GenericClientSessionCtx ctx = newCtx(Set.of(1, 5, 65535));

        GenericClientSessionCtx saved = genericClientSessionCtxService.saveGenericClientSessionCtx(ctx);

        assertThat(saved).isEqualTo(ctx);
        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(ctx.getClientId())).contains(ctx);
        assertThat(genericClientSessionCtxDao.findByClientId(ctx.getClientId())).isEqualTo(ctx);
    }

    @Test
    public void givenContextWithNoPacketIds_whenSaveAndFind_thenEmptySetRoundTrips() {
        GenericClientSessionCtx ctx = newCtx(Set.of());

        genericClientSessionCtxService.saveGenericClientSessionCtx(ctx);

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(ctx.getClientId()))
                .hasValueSatisfying(found -> assertThat(found.getQos2PublishPacketIds()).isEmpty());
    }

    @Test
    public void givenSavedContext_whenSaveSameClientAgain_thenReplacesIt() {
        GenericClientSessionCtx first = newCtx(Set.of(1, 2));
        genericClientSessionCtxService.saveGenericClientSessionCtx(first);
        GenericClientSessionCtx second = first.toBuilder().lastUpdatedTime(first.getLastUpdatedTime() + 1000).qos2PublishPacketIds(Set.of(7)).build();

        genericClientSessionCtxService.saveGenericClientSessionCtx(second);

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(first.getClientId())).contains(second);
    }

    @Test
    public void givenNoContext_whenFind_thenEmpty() {
        String clientId = UUID.randomUUID().toString();

        assertThat(genericClientSessionCtxDao.findByClientId(clientId)).isNull();
        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(clientId)).isEmpty();
    }

    @Test
    public void givenSavedContext_whenFindAsync_thenFutureHasIt() throws Exception {
        GenericClientSessionCtx ctx = newCtx(Set.of(3, 4));
        genericClientSessionCtxService.saveGenericClientSessionCtx(ctx);

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtxAsync(ctx.getClientId()).get()).isEqualTo(ctx);
    }

    @Test
    public void givenNoContext_whenFindAsync_thenFutureHasNull() throws Exception {
        assertThat(genericClientSessionCtxService.findGenericClientSessionCtxAsync(UUID.randomUUID().toString()).get()).isNull();
    }

    @Test
    public void givenSavedContexts_whenFindAll_thenContainsThem() {
        GenericClientSessionCtx first = newCtx(Set.of(1));
        GenericClientSessionCtx second = newCtx(Set.of(2, 3));
        genericClientSessionCtxService.saveGenericClientSessionCtx(first);
        genericClientSessionCtxService.saveGenericClientSessionCtx(second);

        assertThat(genericClientSessionCtxDao.findAll()).contains(first, second);
    }

    @Test
    public void givenManyContexts_whenSaveAll_thenEachIsStoredAndExistingOnesAreReplaced() {
        GenericClientSessionCtx existing = newCtx(Set.of(9));
        genericClientSessionCtxService.saveGenericClientSessionCtx(existing);
        GenericClientSessionCtx replacement = existing.toBuilder().qos2PublishPacketIds(Set.of(10, 11)).build();
        GenericClientSessionCtx added = newCtx(Set.of(1, 2, 3));

        genericClientSessionCtxService.saveAllGenericClientSessionCtx(List.of(replacement, added));

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(existing.getClientId())).contains(replacement);
        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(added.getClientId())).contains(added);
    }

    @Test
    public void givenSavedContexts_whenDeleteOne_thenOnlyThatOneIsGone() {
        GenericClientSessionCtx removed = newCtx(Set.of(1));
        GenericClientSessionCtx kept = newCtx(Set.of(2));
        genericClientSessionCtxService.saveGenericClientSessionCtx(removed);
        genericClientSessionCtxService.saveGenericClientSessionCtx(kept);

        genericClientSessionCtxService.deleteGenericClientSessionCtx(removed.getClientId());

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(removed.getClientId())).isEmpty();
        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx(kept.getClientId())).contains(kept);
    }

    @Test
    public void givenContextWithoutPacketIds_whenSave_thenRejected() {
        GenericClientSessionCtx ctx = newCtx(null);

        assertThatThrownBy(() -> genericClientSessionCtxService.saveGenericClientSessionCtx(ctx))
                .isInstanceOf(DataValidationException.class);
    }

    private GenericClientSessionCtx newCtx(Set<Integer> packetIds) {
        String clientId = "ctx-contract-" + UUID.randomUUID();
        createdClientIds.add(clientId);
        return GenericClientSessionCtx.builder()
                .clientId(clientId)
                .lastUpdatedTime(1_700_000_000_123L)
                .qos2PublishPacketIds(packetIds == null ? null : new HashSet<>(packetIds))
                .build();
    }

    @Test
    public void givenNoContext_whenRemoveInDao_thenDoesNotThrow() {
        assertThatCode(() -> genericClientSessionCtxDao.remove("missing")).doesNotThrowAnyException();
    }

    @Test
    public void givenNoContext_whenDelete_thenNothingHappens() {
        genericClientSessionCtxService.deleteGenericClientSessionCtx("missing");

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx("missing")).isEmpty();
    }

}
