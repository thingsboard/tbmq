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
package org.thingsboard.mqtt.broker.service.processing.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.processing.MsgProcessingCallback;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentMsgSubscriptionsTest {

    private MsgProcessingCallback callback;
    private PersistentMsgSubscriptions subscriptions;
    private PublishMsgProto atMostOnceMsg;
    private PublishMsgProto atLeastOnceMsg;

    @BeforeEach
    void setup() {
        callback = mock(MsgProcessingCallback.class);
        subscriptions = new PersistentMsgSubscriptions(false, null);

        atMostOnceMsg = mock(PublishMsgProto.class);
        when(atMostOnceMsg.getQos()).thenReturn(MqttQoS.AT_MOST_ONCE.value());

        atLeastOnceMsg = mock(PublishMsgProto.class);
        when(atLeastOnceMsg.getQos()).thenReturn(MqttQoS.AT_LEAST_ONCE.value());
    }

    private Subscription mockSub(ClientType type, boolean persistentSession, int qos) {
        Subscription sub = mock(Subscription.class);
        when(sub.getClientType()).thenReturn(type);
        when(sub.getQos()).thenReturn(qos);

        ClientSessionInfo sessionInfo = mock(ClientSessionInfo.class);
        when(sessionInfo.isPersistent()).thenReturn(persistentSession);
        when(sub.getClientSessionInfo()).thenReturn(sessionInfo);

        return sub;
    }

    @Test
    void givenIntegration_whenPubQos0_thenSubscriptionIsStoredAndNoImmediateCallback() {
        Subscription sub = mockSub(ClientType.INTEGRATION, true, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atMostOnceMsg, callback);

        assertThat(subscriptions.getIntegrationSubscriptions().size()).isEqualTo(1);
        verify(callback, never()).accept(sub);
    }

    @Test
    void givenPersistentDev_whenPubQos0_thenTriggersCallbackDirectly() {
        Subscription sub = mockSub(ClientType.DEVICE, true, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atMostOnceMsg, callback);

        verify(callback).accept(sub);
        assertNull(subscriptions.getDeviceSubscriptions());
    }

    @Test
    void givenPersistentApp_whenPubQos0_thenTriggersCallbackDirectly() {
        Subscription sub = mockSub(ClientType.APPLICATION, true, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atMostOnceMsg, callback);

        verify(callback).accept(sub);
        assertNull(subscriptions.getApplicationSubscriptions());
    }

    @Test
    void givenPersistentSubAndDeviceQos1_whenPubQos1_thenSubscriptionIsStoredAndNoImmediateCallback() {
        Subscription sub = mockSub(ClientType.DEVICE, true, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atLeastOnceMsg, callback);

        assertThat(subscriptions.getDeviceSubscriptions().size()).isEqualTo(1);
        verify(callback, never()).accept(sub);
    }

    @Test
    void givenPersistentSubAndAppQos1_whenPubQos1_thenSubscriptionIsStoredAndNoImmediateCallback() {
        Subscription sub = mockSub(ClientType.APPLICATION, true, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atLeastOnceMsg, callback);

        assertThat(subscriptions.getApplicationSubscriptions().size()).isEqualTo(1);
        verify(callback, never()).accept(sub);
    }

    @Test
    void givenIntegrationSub_whenPubQos1_thenSubscriptionIsStoredAndNoImmediateCallback() {
        Subscription sub = mockSub(ClientType.INTEGRATION, true, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atLeastOnceMsg, callback);

        assertThat(subscriptions.getIntegrationSubscriptions().size()).isEqualTo(1);
        verify(callback, never()).accept(sub);
    }

    @Test
    void givenPersistentSubAndAppQos0_whenPubQos1_thenTriggersCallbackDirectly() {
        Subscription sub = mockSub(ClientType.APPLICATION, true, MqttQoS.AT_MOST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atLeastOnceMsg, callback);

        assertNull(subscriptions.getApplicationSubscriptions());
        verify(callback).accept(sub);
    }

    @Test
    void givenNonPersistentDevSession_whenQos1_thenTriggersCallbackDirectly() {
        Subscription sub = mockSub(ClientType.DEVICE, false, MqttQoS.AT_LEAST_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atLeastOnceMsg, callback);

        verify(callback).accept(sub);
        assertNull(subscriptions.getDeviceSubscriptions());
    }

    @Test
    void givenPersistentSub_whenApplicationQos2_thenSubscriptionIsStored() {
        Subscription sub = mockSub(ClientType.APPLICATION, true, MqttQoS.EXACTLY_ONCE.value());

        subscriptions.processSubscriptions(List.of(sub), atLeastOnceMsg, callback);

        assertThat(subscriptions.getApplicationSubscriptions().size()).isEqualTo(1);
        verify(callback, never()).accept(sub);
    }

    @Test
    void givenParallelProcessingWithIntegrationAndDevQos0_whenPubQos0_thenTriggersCallbackAndStoreSubscription() {
        PersistentMsgSubscriptions parallelSubs = new PersistentMsgSubscriptions(true, null);

        Subscription sub1 = mockSub(ClientType.INTEGRATION, true, MqttQoS.AT_LEAST_ONCE.value());
        Subscription sub2 = mockSub(ClientType.DEVICE, true, MqttQoS.AT_MOST_ONCE.value());

        parallelSubs.processSubscriptions(List.of(sub1, sub2), atMostOnceMsg, callback);

        verify(callback).accept(any());
        assertThat(parallelSubs.getIntegrationSubscriptions().size()).isEqualTo(1);
    }

    @RepeatedTest(10)
        // repeat to increase chances of race triggering
    void givenParallelProcessing_whenManyApplicationSubs_thenShouldNotThrowOrLoseData() throws Exception {
        int threads = 20;
        int subsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(true, null);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        Runnable task = () -> {
            try {
                ready.countDown();     // signal ready
                start.await();         // wait for go
                for (int i = 0; i < subsPerThread; i++) {
                    Subscription sub = mockSub(ClientType.APPLICATION, true, MqttQoS.AT_LEAST_ONCE.value());
                    subscriptions.addToApplications(sub, threads * subsPerThread);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        };

        for (int i = 0; i < threads; i++) {
            executor.submit(task);
        }

        ready.await(); // wait for all threads to be ready
        start.countDown(); // release all threads
        done.await(); // wait for all to finish

        executor.shutdownNow();

        // Expecting threads * subsPerThread subscriptions stored
        assertThat(subscriptions.getApplicationSubscriptions()).hasSize(threads * subsPerThread);
    }

}
