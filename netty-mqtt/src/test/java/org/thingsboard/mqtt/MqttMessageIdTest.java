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
package org.thingsboard.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.thingsboard.mqtt.MqttChannelHandlerTest.DIRECT_EXECUTOR;

/**
 * Message ID allocation on a client that never connects: an entry registered in the pending maps holds its ID until
 * removed, as it would until acknowledged.
 */
class MqttMessageIdTest {

    static final int MESSAGE_IDS = 0xffff;
    static final String TOPIC = "message-id/topic";

    EventLoopGroup eventLoop;
    MqttClientImpl client;

    @BeforeEach
    void setup() {
        eventLoop = new NioEventLoopGroup(1);
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[MqttMessageId]");
        clientConfig.setClientId("message-id");
        client = new MqttClientImpl(clientConfig, null, DIRECT_EXECUTOR);
        client.setEventLoop(eventLoop);
    }

    @AfterEach
    void cleanup() {
        eventLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS);
    }

    @Test
    void aNewPublishSkipsTheMessageIdOfOneStillPending() {
        // GIVEN
        MqttPendingPublish held = registerPublish();

        // WHEN - every other ID is taken and freed again, so the next one wraps around to the held one
        for (int i = 1; i < MESSAGE_IDS; i++) {
            client.releaseIfRemoved(registerPublish());
        }
        MqttPendingPublish next = registerPublish();

        // THEN
        assertThat(client.getPendingPublishes()).describedAs("the held publish stays pending").containsEntry(held.getMessageId(), held);
        assertThat(next.getMessageId()).isNotEqualTo(held.getMessageId()).isBetween(1, MESSAGE_IDS);
    }

    @Test
    void aNewPublishSkipsTheMessageIdOfAPendingSubscribe() {
        // GIVEN - made before the first connect(), the SUBSCRIBE stays pending for its CONNACK
        client.on(TOPIC, msg -> null);
        int subscribeId = client.getPendingSubscriptions().keySet().iterator().next();

        // WHEN
        Set<Integer> publishIds = new HashSet<>();
        for (int i = 1; i < MESSAGE_IDS; i++) {
            publishIds.add(registerPublish().getMessageId());
        }

        // THEN
        assertThat(publishIds).describedAs("distinct publish IDs").hasSize(MESSAGE_IDS - 1).doesNotContain(subscribeId);
    }

    @Test
    void aPublishFailsWhenEveryMessageIdIsInUse() {
        // GIVEN
        fillMessageIds();
        ByteBuf payload = Unpooled.copiedBuffer("payload", StandardCharsets.UTF_8);

        // WHEN
        Future<Void> future = client.publish(TOPIC, payload, MqttQoS.AT_LEAST_ONCE, false);

        // THEN
        assertThat(future.awaitUninterruptibly(5, TimeUnit.SECONDS)).describedAs("publish future completed").isTrue();
        assertThat(future.cause()).isInstanceOf(MessageIdsExhaustedException.class);
        assertThat(payload.refCnt()).describedAs("references left on the payload").isZero();
        assertThat(client.getPendingPublishes()).describedAs("pending publishes").hasSize(MESSAGE_IDS);
    }

    @Test
    void aSubscribeFailsWhenEveryMessageIdIsInUse() {
        // GIVEN
        fillMessageIds();

        // WHEN
        Future<MqttQoS> future = client.on(TOPIC, msg -> null);

        // THEN
        assertThat(future.awaitUninterruptibly(5, TimeUnit.SECONDS)).describedAs("subscribe future completed").isTrue();
        assertThat(future.cause()).isInstanceOf(MessageIdsExhaustedException.class);
        assertThat(client.getPendingSubscriptions()).describedAs("pending subscriptions").isEmpty();
        assertThat(client.getPendingSubscribeTopics()).describedAs("pending subscribe topics").isEmpty();
    }

    @Test
    void anUnsubscribeFailsWhenEveryMessageIdIsInUse() {
        // GIVEN - the grant a SUBACK would have recorded
        client.getServerSubscriptions().put(TOPIC, MqttQoS.AT_LEAST_ONCE);
        fillMessageIds();

        // WHEN
        Future<Void> future = client.off(TOPIC);

        // THEN
        assertThat(future.awaitUninterruptibly(5, TimeUnit.SECONDS)).describedAs("unsubscribe future completed").isTrue();
        assertThat(future.cause()).isInstanceOf(MessageIdsExhaustedException.class);
        assertThat(client.getPendingServerUnsubscribes()).describedAs("pending unsubscribes").isEmpty();
    }

    private void fillMessageIds() {
        for (int i = 0; i < MESSAGE_IDS; i++) {
            registerPublish();
        }
        assertThat(client.getPendingPublishes()).describedAs("pending publishes holding distinct IDs").hasSize(MESSAGE_IDS);
    }

    /**
     * Registers a QoS 1 publish, left pending and unsent. Its payload is the empty buffer, whose reference count is
     * inert, so an entry never removed leaks nothing.
     */
    private MqttPendingPublish registerPublish() {
        return client.registerPendingPublish(TOPIC, Unpooled.EMPTY_BUFFER, MqttQoS.AT_LEAST_ONCE, false);
    }

}
