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
package org.thingsboard.mqtt.broker.standalone.local;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionStatsCleanupProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandConsumer;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsApplier;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkConsumer;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LocalMechanismsTest {

    private static final String CLIENT_ID = "client-1";

    @Test
    void disconnectOnSessionConflictSendsTheClusterConflictReasonToTheClientActor() {
        ClientMqttActorManager actorManager = mock(ClientMqttActorManager.class);
        UUID sessionId = UUID.randomUUID();

        new LocalDisconnectClientCommandService(actorManager)
                .disconnectOnSessionConflict("ignored-service", CLIENT_ID, sessionId, true);

        MqttDisconnectMsg msg = captureDisconnect(actorManager);
        assertThat(msg.getSessionId()).isEqualTo(sessionId);
        assertThat(msg.getReason().getType()).isEqualTo(DisconnectReasonType.ON_CLUSTER_CONFLICTING_SESSIONS);
        assertThat(msg.isNewSessionCleanStart()).isTrue();
    }

    @Test
    void disconnectOnSessionConflictKeepsTheCleanStartFlagOfTheNewSession() {
        ClientMqttActorManager actorManager = mock(ClientMqttActorManager.class);

        new LocalDisconnectClientCommandService(actorManager)
                .disconnectOnSessionConflict("ignored-service", CLIENT_ID, UUID.randomUUID(), false);

        assertThat(captureDisconnect(actorManager).isNewSessionCleanStart()).isFalse();
    }

    @Test
    void disconnectOnAdminActionSendsTheAdministrativeReasonToTheClientActor() {
        ClientMqttActorManager actorManager = mock(ClientMqttActorManager.class);
        UUID sessionId = UUID.randomUUID();

        new LocalDisconnectClientCommandService(actorManager)
                .disconnectSessionOnAdminAction("ignored-service", CLIENT_ID, sessionId, false);

        MqttDisconnectMsg msg = captureDisconnect(actorManager);
        assertThat(msg.getSessionId()).isEqualTo(sessionId);
        assertThat(msg.getReason().getType()).isEqualTo(DisconnectReasonType.ON_ADMINISTRATIVE_ACTION);
        assertThat(msg.isNewSessionCleanStart()).isFalse();
    }

    @Test
    void broadcastAppliesTheNotificationOnceWithThisNodesServiceId() {
        InternodeNotificationsApplier applier = mock(InternodeNotificationsApplier.class);
        ServiceInfoProvider serviceInfoProvider = mock(ServiceInfoProvider.class);
        when(serviceInfoProvider.getServiceId()).thenReturn("lite-node");
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setClientSessionStatsCleanupProto(ClientSessionStatsCleanupProto.getDefaultInstance())
                .build();

        new LocalInternodeNotificationsService(applier, serviceInfoProvider).broadcast(proto);

        verify(applier).apply(eq("lite-node"), eq(proto));
        verifyNoMoreInteractions(applier);
    }

    @Test
    void broadcastPropagatesAnApplierFailure() {
        InternodeNotificationsApplier applier = mock(InternodeNotificationsApplier.class);
        ServiceInfoProvider serviceInfoProvider = mock(ServiceInfoProvider.class);
        when(serviceInfoProvider.getServiceId()).thenReturn("lite-node");
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setClientSessionStatsCleanupProto(ClientSessionStatsCleanupProto.getDefaultInstance())
                .build();
        IllegalStateException failure = new IllegalStateException("applier failed");
        doThrow(failure).when(applier).apply(any(), any());

        // as in the cluster service: the caller that saved the change learns that it was not applied
        assertThatThrownBy(() -> new LocalInternodeNotificationsService(applier, serviceInfoProvider).broadcast(proto))
                .isSameAs(failure);

        verify(applier).apply(eq("lite-node"), eq(proto));
    }

    @Test
    void noOpConsumersStartWithoutFailing() {
        List<Runnable> starts = List.of(
                new NoOpDisconnectClientCommandConsumer()::startConsuming,
                new NoOpBasicDownLinkConsumer()::startConsuming,
                new NoOpPersistentDownLinkConsumer()::startConsuming,
                new NoOpInternodeNotificationsConsumer()::startConsuming);

        // none of them has a collaborator, so there is nothing to touch; starting must simply return
        starts.forEach(Runnable::run);
    }

    @Test
    void noOpConsumersImplementTheClusterConsumerContractsAndAreStandaloneOnly() {
        assertThat(new NoOpDisconnectClientCommandConsumer()).isInstanceOf(DisconnectClientCommandConsumer.class);
        assertThat(new NoOpBasicDownLinkConsumer()).isInstanceOf(BasicDownLinkConsumer.class);
        assertThat(new NoOpPersistentDownLinkConsumer()).isInstanceOf(PersistentDownLinkConsumer.class);
        assertThat(new NoOpInternodeNotificationsConsumer()).isInstanceOf(InternodeNotificationsConsumer.class);
        for (Class<?> type : List.of(LocalDisconnectClientCommandService.class, LocalInternodeNotificationsService.class,
                NoOpDisconnectClientCommandConsumer.class, NoOpBasicDownLinkConsumer.class,
                NoOpPersistentDownLinkConsumer.class, NoOpInternodeNotificationsConsumer.class)) {
            assertThat(type.isAnnotationPresent(StandaloneComponent.class)).as(type.getSimpleName()).isTrue();
        }
    }

    private static MqttDisconnectMsg captureDisconnect(ClientMqttActorManager actorManager) {
        ArgumentCaptor<MqttDisconnectMsg> captor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(actorManager).disconnect(eq(CLIENT_ID), captor.capture());
        verifyNoMoreInteractions(actorManager);
        return captor.getValue();
    }

}
