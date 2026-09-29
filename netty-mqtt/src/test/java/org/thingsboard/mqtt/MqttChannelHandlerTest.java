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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MqttChannelHandlerTest {

    // runs each handler inline, so a PUBLISH has reached its handler by the time writeInbound returns
    static final ListeningExecutor DIRECT_EXECUTOR = new ListeningExecutor() {
        @Override
        public <T> ListenableFuture<T> executeAsync(Callable<T> task) {
            return Futures.submit(task, MoreExecutors.directExecutor());
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    EmbeddedChannel channel;

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    @Test
    void aDuplicateQoS2PublishIsReleasedWithoutReachingTheHandler() {
        // GIVEN
        // the handler holds on to the first PUBLISH, so its packet id stays in use
        SettableFuture<Void> handled = SettableFuture.create();
        AtomicInteger handlerCalls = new AtomicInteger();
        MqttHandler handler = msg -> {
            handlerCalls.incrementAndGet();
            return handled;
        };
        channel = newChannel(handler);
        ByteBuf first = Unpooled.copiedBuffer("first", StandardCharsets.UTF_8);
        channel.writeInbound(qos2Publish(1, false, first));
        assertThat(handlerCalls).hasValue(1);

        // WHEN - the broker resends it while the handler still holds the first
        ByteBuf duplicate = Unpooled.copiedBuffer("duplicate", StandardCharsets.UTF_8);
        channel.writeInbound(qos2Publish(1, true, duplicate));

        // THEN
        assertThat(handlerCalls).describedAs("the duplicate reaches no handler").hasValue(1);
        assertThat(duplicate.refCnt()).describedAs("references left on the duplicate's payload").isZero();
        assertThat(first.refCnt()).describedAs("the first payload stays valid until its handler's future completes").isOne();
        handled.set(null);
        assertThat(first.refCnt()).describedAs("references left on the first payload").isZero();
    }

    /**
     * A channel with the inbound pipeline the client builds, minus the codec: MqttPingHandler retains each message it
     * passes on, and MqttChannelHandler handles it.
     */
    private static EmbeddedChannel newChannel(MqttHandler defaultHandler) {
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[MqttChannelHandler]");
        clientConfig.setClientId("channel-handler");
        var client = new MqttClientImpl(clientConfig, defaultHandler, DIRECT_EXECUTOR);
        return new EmbeddedChannel(new MqttPingHandler(clientConfig.getTimeoutSeconds()),
                new MqttChannelHandler(client, ImmediateEventExecutor.INSTANCE.newPromise()));
    }

    private static MqttPublishMessage qos2Publish(int packetId, boolean dup, ByteBuf payload) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, dup, MqttQoS.EXACTLY_ONCE, false, 0);
        return new MqttPublishMessage(fixedHeader, new MqttPublishVariableHeader("channel-handler/topic", packetId), payload);
    }

}
