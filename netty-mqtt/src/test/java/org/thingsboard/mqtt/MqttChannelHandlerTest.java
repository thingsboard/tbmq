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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubRec;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubRel;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Runs nothing until told: every submitted task waits for {@link #runAll()}. */
    static final class HeldExecutor implements ListeningExecutor {

        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public <T> ListenableFuture<T> executeAsync(Callable<T> task) {
            ListenableFutureTask<T> futureTask = ListenableFutureTask.create(task);
            tasks.add(futureTask);
            return futureTask;
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            for (Runnable task; (task = tasks.poll()) != null; ) {
                task.run();
            }
        }
    }

    EmbeddedChannel channel;
    MqttClientImpl client;
    final List<EmbeddedChannel> otherChannels = new ArrayList<>();

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
        otherChannels.forEach(EmbeddedChannel::finishAndReleaseAll);
        otherChannels.clear();
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

    @Test
    void aResendWhileTheOriginalIsInProgressGetsItsPubrecOnlyWithTheOriginalsResult() {
        // GIVEN - the original is still being handled
        SettableFuture<Void> handled = SettableFuture.create();
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(msg -> {
            calls.incrementAndGet();
            return handled;
        }, MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT
        channel.writeInbound(publish("q2/in-progress", MqttQoS.EXACTLY_ONCE, 1, false, payload("original")));

        // WHEN
        channel.writeInbound(publish("q2/in-progress", MqttQoS.EXACTLY_ONCE, 1, true, payload("resent")));

        // THEN - no PUBREC may go out before the original has a result
        assertThat((Object) channel.readOutbound()).describedAs("a PUBREC before the original's result").isNull();
        handled.set(null);
        MqttMessage first = channel.readOutbound();
        MqttMessage second = channel.readOutbound();
        assertThat(List.of(first, second)).allSatisfy(pubrec -> {
            assertThat(pubrec.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBREC);
            assertThat(messageId(pubrec)).isEqualTo(1);
            assertThat(reasonCode(pubrec)).describedAs("the original's code").isEqualTo(PubRec.SUCCESS.byteValue());
        });
        assertThat(calls).hasValue(1);
    }

    @Test
    void aResendOfAnAcknowledgedMessageIsAnsweredWithItsCodeNotPacketIdInUse() {
        // GIVEN - the original was handled and got its PUBREC; no PUBREL yet
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(msg -> {
            calls.incrementAndGet();
            return Futures.immediateVoidFuture();
        }, MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT
        channel.writeInbound(publish("q2/acknowledged", MqttQoS.EXACTLY_ONCE, 1, false, payload("original")));
        channel.readOutbound(); // its PUBREC

        // WHEN
        channel.writeInbound(publish("q2/acknowledged", MqttQoS.EXACTLY_ONCE, 1, true, payload("resent")));

        // THEN - 0x91 would tell the server the message failed
        MqttMessage pubrec = channel.readOutbound();
        assertThat(messageId(pubrec)).isEqualTo(1);
        assertThat(reasonCode(pubrec)).isEqualTo(PubRec.SUCCESS.byteValue());
        assertThat(calls).hasValue(1);
    }

    @Test
    void theReceiveStateSurvivesAReconnectAndIsClearedByASessionLessConnack() {
        // GIVEN - a QoS 2 message handled on the first connection, never released by a PUBREL
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(msg -> {
            calls.incrementAndGet();
            return Futures.immediateVoidFuture();
        }, MqttVersion.MQTT_3_1_1);
        channel.writeInbound(publish("q2/state", MqttQoS.EXACTLY_ONCE, 1, false, payload("first")));

        // WHEN - a reconnect to the kept session, where the server resends it
        EmbeddedChannel kept = newChannelFor(client);
        kept.writeInbound(connAck(true));
        kept.writeInbound(publish("q2/state", MqttQoS.EXACTLY_ONCE, 1, true, payload("first, resent")));

        // THEN
        assertThat(calls).describedAs("handled across the reconnect").hasValue(1);
        assertThat(written(kept)).contains("PUBREC 1");

        // WHEN - a reconnect that lost the session, where id 1 carries a new message
        EmbeddedChannel fresh = newChannelFor(client);
        fresh.writeInbound(connAck(false));
        fresh.writeInbound(publish("q2/state", MqttQoS.EXACTLY_ONCE, 1, false, payload("second")));

        // THEN
        assertThat(calls).describedAs("the new message is handled").hasValue(2);
    }

    @Test
    void aLateResultFromAPreviousSessionLeavesTheNewSessionsStateAlone() {
        // GIVEN - handlers held open, by payload
        Map<String, SettableFuture<Void>> handling = new ConcurrentHashMap<>();
        channel = newChannel(msg -> {
            SettableFuture<Void> result = SettableFuture.create();
            handling.put(msg.payload().toString(StandardCharsets.UTF_8), result);
            return result;
        }, MqttVersion.MQTT_5);
        channel.writeInbound(publish("q2/late", MqttQoS.EXACTLY_ONCE, 1, false, payload("old")));
        EmbeddedChannel reconnected = newChannelFor(client);
        reconnected.writeInbound(connAck(false)); // the server lost the session: id 1 is free for a new message
        reconnected.writeInbound(publish("q2/late", MqttQoS.EXACTLY_ONCE, 1, false, payload("new")));

        // WHEN - the old message fails late; under MQTT 5 a failure ends its exchange and removes its entry
        handling.get("old").setException(new IllegalStateException("late failure"));

        // THEN - the entry removed is the old one, not the new message's under the same id
        reconnected.writeInbound(publish("q2/late", MqttQoS.EXACTLY_ONCE, 1, true, payload("new, resent")));
        assertThat(handling).describedAs("messages handled").containsOnlyKeys("old", "new");
    }

    @Test
    void disconnectClearsTheReceiveState() {
        // GIVEN
        channel = newChannel(msg -> Futures.immediateVoidFuture(), MqttVersion.MQTT_3_1_1);
        channel.writeInbound(publish("q2/disconnect", MqttQoS.EXACTLY_ONCE, 1, false, payload("handled")));
        assertThat(client.getQos2Received()).containsKey(1);

        // WHEN
        client.disconnect();

        // THEN
        assertThat(client.getQos2Received()).isEmpty();
    }

    @Test
    void aPubrelReleasesThePacketIdForANewMessage() {
        // GIVEN
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(msg -> {
            calls.incrementAndGet();
            return Futures.immediateVoidFuture();
        }, MqttVersion.MQTT_3_1_1);
        channel.writeInbound(publish("q2/pubrel", MqttQoS.EXACTLY_ONCE, 1, false, payload("first")));

        // WHEN
        channel.writeInbound(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(1)));
        channel.writeInbound(publish("q2/pubrel", MqttQoS.EXACTLY_ONCE, 1, false, payload("second")));

        // THEN
        assertThat(written(channel)).contains("PUBCOMP 1");
        assertThat(calls).hasValue(2);
    }

    @Test
    void aPubrecForAnUnknownPacketIdIsAnsweredWithPubrel() {
        // GIVEN - no publish is pending, e.g. its retransmissions ran out before the broker's PUBREC arrived
        channel = newChannel(null);
        channel.readOutbound(); // the CONNECT channelActive wrote

        // WHEN
        channel.writeInbound(pubrec(7));

        // THEN - the broker holds the packet id until it gets a PUBREL
        MqttMessage pubrel = channel.readOutbound();
        assertThat(pubrel).describedAs("reply to the PUBREC").isNotNull();
        assertThat(pubrel.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBREL);
        assertThat(pubrel.fixedHeader().qosLevel()).describedAs("PUBREL fixed header flags are 0010").isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(((MqttMessageIdVariableHeader) pubrel.variableHeader()).messageId()).isEqualTo(7);
    }

    @Test
    void aPubrecForAnUnknownPacketIdIsAnsweredWithPacketIdNotFoundUnderMqtt5() {
        // GIVEN
        channel = newChannel(null, MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT channelActive wrote

        // WHEN
        channel.writeInbound(pubrec(7));

        // THEN
        MqttMessage pubrel = channel.readOutbound();
        assertThat(pubrel).describedAs("reply to the PUBREC").isNotNull();
        assertThat(pubrel.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBREL);
        assertThat(pubrel.variableHeader()).isInstanceOfSatisfying(MqttPubReplyMessageVariableHeader.class, header -> {
            assertThat(header.messageId()).isEqualTo(7);
            assertThat(header.reasonCode()).isEqualTo(PubRel.PACKET_IDENTIFIER_NOT_FOUND.byteValue());
        });
    }

    @Test
    void aPubrecAcceptingAQoS2PublishIsAnsweredWithAPubrelOfItsOwnUnderMqtt5() {
        // GIVEN
        channel = newChannel(null, MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT channelActive wrote
        MqttPendingPublish publish = registerWrittenQos2Publish(Unpooled.EMPTY_BUFFER);

        // WHEN - accepted, with a reason code and a property that are the PUBREC's own
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.REASON_STRING.value(), "no subscribers"));
        channel.writeInbound(pubrec(publish.getMessageId(), PubRec.NO_MATCHING_SUBSCRIBERS.byteValue(), properties));

        // THEN
        MqttMessage pubrel = channel.readOutbound();
        assertThat(pubrel).describedAs("reply to the PUBREC").isNotNull();
        assertThat(pubrel.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBREL);
        assertThat(pubrel.variableHeader()).isInstanceOfSatisfying(MqttPubReplyMessageVariableHeader.class, header -> {
            assertThat(header.messageId()).isEqualTo(publish.getMessageId());
            assertThat(header.reasonCode()).isEqualTo(PubRel.SUCCESS.byteValue());
            assertThat(header.properties().isEmpty()).describedAs("PUBREL carries no properties").isTrue();
        });
        assertThat(publish.getFuture().isDone()).describedAs("publish completed before its PUBCOMP").isFalse();
    }

    @Test
    void aPubrecRefusingAQoS2PublishFailsItWithoutAPubrelUnderMqtt5() {
        // GIVEN
        channel = newChannel(null, MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT channelActive wrote
        ByteBuf payload = Unpooled.copiedBuffer("refused", StandardCharsets.UTF_8);
        MqttPendingPublish publish = registerWrittenQos2Publish(payload);

        // WHEN
        channel.writeInbound(pubrec(publish.getMessageId(), PubRec.QUOTA_EXCEEDED.byteValue(), MqttProperties.NO_PROPERTIES));

        // THEN - the QoS 2 exchange ends with the refusal, which frees the message ID
        assertThat((Object) channel.readOutbound()).describedAs("reply to the refusing PUBREC").isNull();
        assertThat(publish.getFuture().cause()).isInstanceOf(MqttPublishFailedException.class).hasMessageContaining("0x97");
        assertThat(client.getPendingPublishes()).describedAs("pending publishes").doesNotContainValue(publish);
        assertThat(payload.refCnt()).describedAs("references left on the payload").isZero();
    }

    @Test
    void aConnectForKeepAliveMinusOneCarriesZero() {
        // GIVEN - -1 is what callers used for "no keep-alive"; CONNECT carries it in two bytes, so it went out as 65535
        MqttClientConfig clientConfig = testConfig(MqttVersion.MQTT_3_1_1);
        clientConfig.setTimeoutSeconds(-1);

        // WHEN - channelActive writes the CONNECT
        channel = newChannel(clientConfig, null, DIRECT_EXECUTOR);

        // THEN
        MqttConnectMessage connect = channel.readOutbound();
        assertThat(connect.variableHeader().keepAliveTimeSeconds()).isZero();
    }

    @Test
    void anMqtt5ConnectThatKeepsTheSessionAsksForItNeverToExpire() {
        // GIVEN - MQTT 5 split Clean Session into Clean Start and a Session Expiry Interval, whose absence means 0: the
        // session ends with the connection [MQTT-3.1.2.11.2], and Clean Start 0 alone never finds one to resume
        MqttClientConfig clientConfig = testConfig(MqttVersion.MQTT_5);
        clientConfig.setCleanSession(false);

        // WHEN - channelActive writes the CONNECT
        channel = newChannel(clientConfig, null, DIRECT_EXECUTOR);

        // THEN - 0xFFFFFFFF, never: what a 3.x Clean Session of 0 means
        MqttConnectMessage connect = channel.readOutbound();
        MqttProperties.MqttProperty<?> expiry = connect.variableHeader().properties()
                .getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
        assertThat(expiry).describedAs("the Session Expiry Interval").isNotNull();
        assertThat(expiry.value()).isEqualTo(0xFFFFFFFF);
    }

    @ParameterizedTest
    @CsvSource({"MQTT_5, true", "MQTT_3_1_1, false", "MQTT_3_1, false"})
    void aConnectCarriesNoSessionExpiryForACleanMqtt5SessionOrUnder3x(MqttVersion version, boolean cleanSession) {
        // GIVEN - a clean MQTT 5 session ends with the connection, which an absent interval says; 3.x has no properties
        MqttClientConfig clientConfig = testConfig(version);
        clientConfig.setCleanSession(cleanSession);

        // WHEN
        channel = newChannel(clientConfig, null, DIRECT_EXECUTOR);

        // THEN
        MqttConnectMessage connect = channel.readOutbound();
        assertThat(connect.variableHeader().properties()
                .getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value())).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = MqttQoS.class, names = {"AT_LEAST_ONCE", "EXACTLY_ONCE"})
    void noAckIsWrittenBeforeTheHandlersFutureHasAResult(MqttQoS qos) {
        // GIVEN
        SettableFuture<Void> handled = SettableFuture.create();
        channel = newChannel(msg -> handled, MqttVersion.MQTT_3_1_1);
        channel.readOutbound(); // the CONNECT

        // WHEN
        channel.writeInbound(publish("pin/ack", qos, 3, false, payload("pending")));

        // THEN
        assertThat((Object) channel.readOutbound()).describedAs("an ack written while the handler's future is pending").isNull();
        handled.set(null);
        MqttMessage ack = channel.readOutbound();
        assertThat(ack.fixedHeader().messageType()).isEqualTo(qos == MqttQoS.AT_LEAST_ONCE ? MqttMessageType.PUBACK : MqttMessageType.PUBREC);
        assertThat(messageId(ack)).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(value = MqttQoS.class, names = {"AT_LEAST_ONCE", "EXACTLY_ONCE"})
    void aFailedHandlerIsAckedWithUnspecifiedErrorUnderMqtt5(MqttQoS qos) {
        // GIVEN
        channel = newChannel(msg -> Futures.immediateFailedFuture(new IllegalStateException("forwarding failed")), MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT

        // WHEN
        channel.writeInbound(publish("pin/ack", qos, 4, false, payload("fails")));

        // THEN
        MqttMessage ack = channel.readOutbound();
        assertThat(messageId(ack)).isEqualTo(4);
        assertThat(reasonCode(ack)).isEqualTo((byte) 0x80);
    }

    @ParameterizedTest
    @EnumSource(value = MqttQoS.class, names = {"AT_LEAST_ONCE", "EXACTLY_ONCE"})
    void aFailedHandlerIsAckedWithAPlainAckUnderMqtt311(MqttQoS qos) {
        // GIVEN
        channel = newChannel(msg -> Futures.immediateFailedFuture(new IllegalStateException("forwarding failed")), MqttVersion.MQTT_3_1_1);
        channel.readOutbound(); // the CONNECT

        // WHEN
        channel.writeInbound(publish("pin/ack", qos, 5, false, payload("fails")));

        // THEN
        MqttMessage ack = channel.readOutbound();
        assertThat(ack.fixedHeader().messageType()).isEqualTo(qos == MqttQoS.AT_LEAST_ONCE ? MqttMessageType.PUBACK : MqttMessageType.PUBREC);
        assertThat(messageId(ack)).isEqualTo(5);
        assertCarriesNoReasonCode(ack);
    }

    @ParameterizedTest
    @EnumSource(value = MqttQoS.class, names = {"AT_MOST_ONCE", "AT_LEAST_ONCE", "EXACTLY_ONCE"})
    void aRunOfFailedHandlersLogsOneWarningAndItsEndOneLine(MqttQoS qos) {
        // GIVEN - a handler that fails, as one forwarding to a store that is down does
        AtomicBoolean failing = new AtomicBoolean(true);
        channel = newChannel(msg -> failing.get()
                ? Futures.immediateFailedFuture(new IllegalStateException("forwarding failed"))
                : Futures.immediateVoidFuture(), MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT
        Logger logger = (Logger) LoggerFactory.getLogger(MqttChannelHandler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            // WHEN - three fail, one is handled, and the next fails again
            for (int id = 1; id <= 3; id++) {
                channel.writeInbound(publish("flood/a", qos, id, false, payload("fails")));
            }
            failing.set(false);
            channel.writeInbound(publish("flood/a", qos, 4, false, payload("handled")));
            failing.set(true);
            channel.writeInbound(publish("flood/a", qos, 5, false, payload("fails")));

            // THEN - one warning per run of failures, and one line when a run ends
            assertThat(logs.list).filteredOn(e -> e.getLevel().isGreaterOrEqual(Level.INFO))
                    .extracting(ILoggingEvent::getLevel)
                    .containsExactly(Level.WARN, Level.INFO, Level.WARN);
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void aHandlerTheExecutorRejectsIsAckedAsAFailure() {
        // GIVEN
        ListeningExecutor rejecting = new ListeningExecutor() {
            @Override
            public <T> ListenableFuture<T> executeAsync(Callable<T> task) {
                throw new RejectedExecutionException("full");
            }

            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("full");
            }
        };
        channel = newChannel(testConfig(MqttVersion.MQTT_5), msg -> Futures.immediateVoidFuture(), rejecting);
        channel.readOutbound(); // the CONNECT
        ByteBuf payload = payload("rejected");

        // WHEN
        channel.writeInbound(publish("pin/ack", MqttQoS.AT_LEAST_ONCE, 6, false, payload));

        // THEN
        MqttMessage ack = channel.readOutbound();
        assertThat(messageId(ack)).isEqualTo(6);
        assertThat(reasonCode(ack)).isEqualTo((byte) 0x80);
        assertThat(payload.refCnt()).describedAs("references left on the payload").isZero();
    }

    @Test
    void aDeliveryQueuedAtDisconnectNeverReachesItsHandlerAndIsNotAcked() {
        // GIVEN - a PUBLISH waiting for the executor
        HeldExecutor executor = new HeldExecutor();
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> {
            calls.incrementAndGet();
            return Futures.immediateVoidFuture();
        }, executor);
        channel.readOutbound(); // the CONNECT
        ByteBuf payload = payload("queued");
        channel.writeInbound(publish("skip/queued", MqttQoS.AT_LEAST_ONCE, 1, false, payload));

        // WHEN
        client.disconnect();
        executor.runAll();

        // THEN
        assertThat(calls).describedAs("handler calls").hasValue(0);
        assertThat(written(channel)).describedAs("never acked, so a kept session redelivers it").isEmpty();
        assertThat(payload.refCnt()).describedAs("references left on the payload").isZero();
    }

    @Test
    void aQoS0DeliveryQueuedAtDisconnectNeverReachesItsHandlerAndLeavesNothingInFlight() {
        // GIVEN - a PUBLISH waiting for the executor, counted as in flight
        HeldExecutor executor = new HeldExecutor();
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> {
            calls.incrementAndGet();
            return Futures.immediateVoidFuture();
        }, executor);
        channel.readOutbound(); // the CONNECT
        ByteBuf payload = payload("queued");
        channel.writeInbound(publish("skip/queued/qos0", MqttQoS.AT_MOST_ONCE, 0, false, payload));
        assertThat(inFlightPublishes()).describedAs("in flight while queued").isOne();

        // WHEN
        client.disconnect();
        executor.runAll();

        // THEN
        assertThat(calls).describedAs("handler calls").hasValue(0);
        assertThat(written(channel)).describedAs("a QoS 0 message has no ack").isEmpty();
        assertThat(payload.refCnt()).describedAs("references left on the payload").isZero();
        assertThat(inFlightPublishes()).describedAs("in flight once skipped").isZero();
    }

    @Test
    void aQoS2DeliveryQueuedAtDisconnectNeverReachesItsHandlerAndIsNotAcked() {
        // GIVEN - a PUBLISH waiting for the executor
        HeldExecutor executor = new HeldExecutor();
        AtomicInteger calls = new AtomicInteger();
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> {
            calls.incrementAndGet();
            return Futures.immediateVoidFuture();
        }, executor);
        channel.readOutbound(); // the CONNECT
        ByteBuf payload = payload("queued");
        channel.writeInbound(publish("skip/queued/qos2", MqttQoS.EXACTLY_ONCE, 1, false, payload));
        assertThat(client.getQos2Received()).containsKey(1);

        // WHEN
        client.disconnect();
        executor.runAll();

        // THEN
        assertThat(calls).describedAs("handler calls").hasValue(0);
        assertThat(written(channel)).describedAs("never acked, so a kept session redelivers it").isEmpty();
        assertThat(payload.refCnt()).describedAs("references left on the payload").isZero();
        assertThat(client.getQos2Received()).describedAs("receive state").doesNotContainKey(1);
        assertThat(inFlightPublishes()).describedAs("in flight once skipped").isZero();
    }

    @Test
    void aQoS2DeliverySkippedAfterDisconnectLeavesAnUnrelatedEntryOfTheSamePacketId() {
        // GIVEN - a PUBLISH waiting for the executor; disconnect() clears the receive state
        HeldExecutor executor = new HeldExecutor();
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> Futures.immediateVoidFuture(), executor);
        channel.readOutbound(); // the CONNECT
        channel.writeInbound(publish("skip/late", MqttQoS.EXACTLY_ONCE, 1, false, payload("queued")));
        client.disconnect();
        ListenableFuture<Byte> unrelated = Futures.immediateFuture((byte) 0);
        client.getQos2Received().put(1, unrelated);

        // WHEN - the skipped delivery's result comes late
        executor.runAll();

        // THEN - it removes only its own entry, and this one is not
        assertThat(client.getQos2Received().get(1)).isSameAs(unrelated);
    }

    @Test
    void aDrainWritesTheAcksOfStartedDeliveriesBeforeItsDisconnect() {
        // GIVEN - message 1's handler has started and is pending; message 2 waits for the executor
        HeldExecutor executor = new HeldExecutor();
        Map<Integer, SettableFuture<Void>> handling = new ConcurrentHashMap<>();
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> {
            SettableFuture<Void> result = SettableFuture.create();
            handling.put(msg.variableHeader().packetId(), result);
            return result;
        }, executor);
        client.setChannel(channel);
        channel.readOutbound(); // the CONNECT
        channel.writeInbound(publish("drain/acks", MqttQoS.AT_LEAST_ONCE, 1, false, payload("started")));
        executor.runAll();
        channel.writeInbound(publish("drain/acks", MqttQoS.AT_LEAST_ONCE, 2, false, payload("queued")));

        // WHEN
        Future<Void> disconnected = client.disconnect(5, TimeUnit.SECONDS);
        assertThat(written(channel)).describedAs("written while message 1 is still being handled").isEmpty();
        handling.get(1).set(null);
        executor.runAll(); // message 2's turn comes after the disconnect

        // THEN
        assertThat(written(channel)).containsExactly("PUBACK 1", "DISCONNECT");
        assertThat(handling).describedAs("handlers called").containsOnlyKeys(1);
        assertThat(disconnected.isDone()).isTrue();
    }

    @Test
    void aDrainGivesUpAtItsTimeout() {
        // GIVEN - a handler that never completes
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> SettableFuture.create(), DIRECT_EXECUTOR);
        client.setChannel(channel);
        channel.readOutbound(); // the CONNECT
        channel.writeInbound(publish("drain/timeout", MqttQoS.AT_LEAST_ONCE, 1, false, payload("stuck")));

        // WHEN
        client.disconnect(5, TimeUnit.SECONDS);
        channel.advanceTimeBy(5, TimeUnit.SECONDS);
        channel.runPendingTasks();

        // THEN
        assertThat(written(channel)).containsExactly("DISCONNECT");
    }

    @Test
    void aPingResponseThatNeverArrivesClosesTheConnection() {
        // GIVEN
        channel = newChannel(pingTestConfig(), msg -> SettableFuture.create(), DIRECT_EXECUTOR);
        channel.readOutbound(); // the CONNECT

        // WHEN - the connection goes idle, and a keep-alive passes with no PINGRESP
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        channel.advanceTimeBy(10, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        // THEN
        assertThat(written(channel)).containsExactly("PINGREQ", "DISCONNECT");
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void aPingResponseLeftUnreadWhileBackpressurePausesReadingKeepsTheConnection() {
        // GIVEN - two messages in flight at a high watermark of 2: reading is paused
        channel = newChannel(pingTestConfig(), msg -> SettableFuture.create(), DIRECT_EXECUTOR);
        channel.readOutbound(); // the CONNECT
        for (int id = 1; id <= 2; id++) {
            channel.writeInbound(publish("ping/paused", MqttQoS.AT_LEAST_ONCE, id, false, payload("in flight")));
        }
        assertThat(channel.config().isAutoRead()).isFalse();

        // WHEN - the connection goes idle, and a keep-alive passes: a PINGRESP would wait unread
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        channel.advanceTimeBy(10, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        // THEN - the PINGREQ still keeps the server's keep-alive
        assertThat(written(channel)).containsExactly("PINGREQ");
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void aPingResponseCheckArmedBeforeBackpressurePausesReadingIsDropped() {
        // GIVEN - a PINGREQ out, its response not yet read
        channel = newChannel(pingTestConfig(), msg -> SettableFuture.create(), DIRECT_EXECUTOR);
        channel.readOutbound(); // the CONNECT
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);

        // WHEN - backpressure pauses reading before the response is read, and a keep-alive passes
        for (int id = 1; id <= 2; id++) {
            channel.writeInbound(publish("ping/paused", MqttQoS.AT_LEAST_ONCE, id, false, payload("in flight")));
        }
        channel.advanceTimeBy(10, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        // THEN
        assertThat(written(channel)).containsExactly("PINGREQ");
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void thePingResponseCheckResumesWithReading() {
        // GIVEN - reading paused through an idle period, then resumed as the messages in flight complete
        Map<Integer, SettableFuture<Void>> handling = new ConcurrentHashMap<>();
        channel = newChannel(pingTestConfig(), msg -> {
            SettableFuture<Void> result = SettableFuture.create();
            handling.put(msg.variableHeader().packetId(), result);
            return result;
        }, DIRECT_EXECUTOR);
        channel.readOutbound(); // the CONNECT
        for (int id = 1; id <= 2; id++) {
            channel.writeInbound(publish("ping/paused", MqttQoS.AT_LEAST_ONCE, id, false, payload("in flight")));
        }
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        handling.values().forEach(result -> result.set(null));
        assertThat(channel.config().isAutoRead()).isTrue();

        // WHEN - the connection goes idle again, and a keep-alive passes with no PINGRESP
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        channel.advanceTimeBy(10, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        // THEN
        assertThat(written(channel)).containsExactly("PINGREQ", "PUBACK 1", "PUBACK 2", "PINGREQ", "DISCONNECT");
        assertThat(channel.isOpen()).isFalse();
    }

    /** Keep-alive 10 s, and backpressure watermarks of 2 and 1: two messages in flight pause reading. */
    private static MqttClientConfig pingTestConfig() {
        MqttClientConfig clientConfig = testConfig(MqttVersion.MQTT_3_1_1);
        clientConfig.setTimeoutSeconds(10);
        clientConfig.setBackPressureLowWatermark(1);
        clientConfig.setBackPressureHighWatermark(2);
        return clientConfig;
    }

    @Test
    void readingStaysPausedWhileADrainWaits() {
        // GIVEN - watermarks of 3 and 2, and three messages in flight: reading is paused
        MqttClientConfig clientConfig = testConfig(MqttVersion.MQTT_3_1_1);
        clientConfig.setBackPressureLowWatermark(2);
        clientConfig.setBackPressureHighWatermark(3);
        Map<Integer, SettableFuture<Void>> handling = new ConcurrentHashMap<>();
        channel = newChannel(clientConfig, msg -> {
            SettableFuture<Void> result = SettableFuture.create();
            handling.put(msg.variableHeader().packetId(), result);
            return result;
        }, DIRECT_EXECUTOR);
        client.setChannel(channel);
        for (int id = 1; id <= 3; id++) {
            channel.writeInbound(publish("drain/backpressure", MqttQoS.AT_LEAST_ONCE, id, false, payload("in flight")));
        }
        assertThat(channel.config().isAutoRead()).isFalse();

        // WHEN - two of them complete during the drain: outside one, falling below the low watermark resumes reading
        client.disconnect(5, TimeUnit.SECONDS);
        handling.get(1).set(null);
        handling.get(2).set(null);

        // THEN
        assertThat(channel.config().isAutoRead()).describedAs("reading during a drain").isFalse();
    }

    @Test
    void disconnectingTwiceReturnsTheSameCloseFuture() {
        // GIVEN
        channel = newChannel(testConfig(MqttVersion.MQTT_3_1_1), msg -> SettableFuture.create(), DIRECT_EXECUTOR);
        client.setChannel(channel);
        channel.writeInbound(publish("drain/twice", MqttQoS.AT_LEAST_ONCE, 1, false, payload("stuck")));

        // WHEN
        Future<Void> first = client.disconnect(5, TimeUnit.SECONDS);
        Future<Void> second = client.disconnect(5, TimeUnit.SECONDS);

        // THEN
        assertThat(second).isSameAs(first);
        channel.advanceTimeBy(5, TimeUnit.SECONDS);
        channel.runPendingTasks();
        assertThat(written(channel)).describedAs("one DISCONNECT, after the CONNECT").containsExactly("CONNECT", "DISCONNECT");
    }

    @Test
    void aPublishArrivingBeforeItsSubAckReachesTheFiltersHandler() {
        // GIVEN - a default handler, and a filter whose SUBSCRIBE still waits for its SUBACK
        List<String> served = new CopyOnWriteArrayList<>();
        channel = newChannel(record(served, "default"), MqttVersion.MQTT_3_1_1);
        client.on("sensors/#", record(served, "sensors/#"), MqttQoS.AT_LEAST_ONCE);

        // WHEN - MQTT 3.1.1 §3.8.4 lets the server send matching PUBLISHes before the SUBACK
        channel.writeInbound(publish("sensors/room1", MqttQoS.AT_LEAST_ONCE, 1, false, payload("early")));

        // THEN
        assertThat(served).containsExactly("sensors/#");
    }

    @Test
    void aRefusedFilterLeavesNoHandlerRegistered() {
        // GIVEN
        List<String> served = new CopyOnWriteArrayList<>();
        channel = newChannel(record(served, "default"), MqttVersion.MQTT_3_1_1);
        Future<MqttQoS> subscribed = client.on("sensors/#", record(served, "sensors/#"), MqttQoS.AT_LEAST_ONCE);
        int packetId = onlyPendingSubscriptionId();

        // WHEN
        channel.writeInbound(subAck(packetId, MqttQoS.FAILURE.value()));

        // THEN
        assertThat(subscribed.cause()).isInstanceOf(MqttSubscriptionFailedException.class);
        assertThat(client.getSubscriptions()).isEmpty();
        channel.writeInbound(publish("sensors/room1", MqttQoS.AT_LEAST_ONCE, 2, false, payload("after the refusal")));
        assertThat(served).containsExactly("default");
    }

    @Test
    void aFilterUnsubscribedWhileItsSubscribeIsInFlightIsUnsubscribedOnTheServerOnceGranted() {
        // GIVEN
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        channel.readOutbound(); // the CONNECT
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        client.on("sensors/#", handler, MqttQoS.AT_LEAST_ONCE);
        int packetId = onlyPendingSubscriptionId();
        Future<Void> unsubscribed = client.off("sensors/#", handler);
        assertThat(unsubscribed.isSuccess()).describedAs("nothing to unsubscribe on the server yet").isTrue();

        // WHEN
        channel.writeInbound(subAck(packetId, MqttQoS.AT_LEAST_ONCE.value()));

        // THEN - the server holds a filter nobody wants any more
        MqttMessage sent = channel.readOutbound();
        assertThat(sent).isInstanceOfSatisfying(MqttUnsubscribeMessage.class,
                unsubscribe -> assertThat(unsubscribe.payload().topics()).containsExactly("sensors/#"));
    }

    @Test
    void aSubscribeFailedByDisconnectLeavesNoHandlerRegistered() {
        // GIVEN
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        Future<MqttQoS> subscribed = client.on("sensors/#", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);

        // WHEN
        client.disconnect();

        // THEN
        assertThat(subscribed.cause()).isInstanceOf(ChannelClosedException.class);
        assertThat(client.getSubscriptions()).isEmpty();
    }

    @Test
    void anOnForAFilterInFlightThatRacesItsFailureRegistersNothing() {
        // GIVEN - a filter whose SUBSCRIBE is in flight, failed by the event loop right after a second on() for it
        // found it pending: the entry and its topic are still where that on() looked them up
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        client.on("sensors/#", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);
        MqttPendingSubscription inFlight = client.getPendingSubscriptions().get(onlyPendingSubscriptionId());
        client.failSubscription(inFlight, new MqttSubscriptionFailedException("refused"));
        client.getPendingSubscribeTopics().add("sensors/#");

        // WHEN
        Future<MqttQoS> subscribed = client.on("sensors/#", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);

        // THEN - a failed on() leaves nothing registered
        assertThat(subscribed.cause()).isInstanceOf(MqttSubscriptionFailedException.class);
        assertThat(client.getSubscriptions()).isEmpty();
    }

    @Test
    void aConnackArrivingAfterDisconnectReportsNoConnectionEvents() {
        // GIVEN - a reconnect whose CONNECT is out when disconnect() runs
        List<String> events = new CopyOnWriteArrayList<>();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        client.setCallback(new MqttClientCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                events.add("connectionLost");
            }

            @Override
            public void onSuccessfulReconnect() {
                events.add("onSuccessfulReconnect");
            }

            @Override
            public void onConnAck(MqttConnAckMessage connAckMessage) {
                events.add("onConnAck");
            }
        });
        channel.writeInbound(connAck(false));
        closeAsTheClientSeesIt(channel);
        Promise<MqttConnectResult> connectFuture = ImmediateEventExecutor.INSTANCE.newPromise();
        EmbeddedChannel reconnecting = new EmbeddedChannel(new MqttPingHandler(client.getClientConfig().getTimeoutSeconds()),
                new MqttChannelHandler(client, connectFuture));
        otherChannels.add(reconnecting);
        events.clear();

        // WHEN - its CONNACK arrives after the disconnect, and the channel then closes
        client.disconnect();
        reconnecting.writeInbound(connAck(false));
        reconnecting.close();
        client.onChannelClosed(reconnecting, "localhost", 1883, 0);

        // THEN - the connection was never up for its owner
        assertThat(events).isEmpty();
        assertThat(connectFuture.cause()).isInstanceOf(ChannelClosedException.class).hasMessage("Client is disconnected");
    }

    @Test
    void aSessionLessConnackResubscribesEveryRegisteredFilterNotAlreadyInFlight() {
        // GIVEN - two filters granted on the first connection, and a third on() still waiting for a CONNACK
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        channel.writeInbound(connAck(false));
        subscribeAndGrant(channel, "a/#", handler);
        subscribeAndGrant(channel, "b/#", handler);
        client.on("c/#", handler, MqttQoS.AT_LEAST_ONCE);
        EmbeddedChannel reconnected = newChannelFor(client);

        // WHEN - the server lost the session: it holds none of the three
        reconnected.writeInbound(connAck(false));

        // THEN - one SUBSCRIBE per filter; c/# only once, although it was already pending
        assertThat(subscribedFilters(reconnected)).containsExactlyInAnyOrder("a/#", "b/#", "c/#");
    }

    @Test
    void aSessionPresentConnackSendsNoSubscribeAndKeepsTheGrants() {
        // GIVEN - both grants set aside by the close of the first connection
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        channel.writeInbound(connAck(false));
        subscribeAndGrant(channel, "a/#", handler);
        subscribeAndGrant(channel, "b/#", handler);
        closeAsTheClientSeesIt(channel);
        assertThat(client.getServerSubscriptions()).describedAs("grants after the close").isEmpty();
        EmbeddedChannel reconnected = newChannelFor(client);

        // WHEN - the server kept the session, and with it both subscriptions
        reconnected.writeInbound(connAck(true));

        // THEN - a second SUBSCRIBE would make the server resend its retained messages [MQTT-3.8.4-3]
        assertThat(subscribedFilters(reconnected)).isEmpty();
        assertThat(client.getServerSubscriptions()).containsOnlyKeys("a/#", "b/#");
    }

    @Test
    void aResubscribeCutShortByACloseIsSentAgainWhenTheNextSessionIsPresent() {
        // GIVEN - a session-less reconnect resubscribed both filters; b/# was granted, then the channel closed before
        // the SUBACK for a/#, whose SUBSCRIBE may never have reached the server
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        channel.writeInbound(connAck(false));
        subscribeAndGrant(channel, "a/#", handler);
        subscribeAndGrant(channel, "b/#", handler);
        EmbeddedChannel resubscribing = newChannelFor(client);
        resubscribing.writeInbound(connAck(false));
        assertThat(subscribedFilters(resubscribing)).containsExactlyInAnyOrder("a/#", "b/#");
        resubscribing.writeInbound(subAck(pendingSubscriptionIdFor("b/#"), MqttQoS.AT_LEAST_ONCE.value()));
        closeAsTheClientSeesIt(resubscribing);
        EmbeddedChannel reconnected = newChannelFor(client);

        // WHEN - the CONNECT of the closed connection created the session this one finds present
        reconnected.writeInbound(connAck(true));

        // THEN - the kept grant is not sent again; the filter it does not hold is
        assertThat(subscribedFilters(reconnected)).containsExactly("a/#");
        assertThat(client.getServerSubscriptions()).containsOnlyKeys("b/#");
    }

    @Test
    void underMqtt31EveryConnackResubscribes() {
        // GIVEN - MQTT 3.1 has no session-present flag, so no CONNACK can tell the session was kept
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        channel = newChannel(null, MqttVersion.MQTT_3_1);
        channel.writeInbound(connAck(false));
        subscribeAndGrant(channel, "a/#", handler);
        EmbeddedChannel reconnected = newChannelFor(client);

        // WHEN
        reconnected.writeInbound(connAck(true));

        // THEN
        assertThat(subscribedFilters(reconnected)).containsExactly("a/#");
    }

    @Test
    void aRefusedResubscribeIsReportedAndKeepsTheFilterRegistered() {
        // GIVEN
        List<String> failures = new CopyOnWriteArrayList<>();
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        client.setCallback(new MqttClientCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void onSuccessfulReconnect() {
            }

            @Override
            public void onResubscribeFailed(String topicFilter, Throwable cause) {
                failures.add(topicFilter + " " + cause.getClass().getSimpleName());
            }
        });
        channel.writeInbound(connAck(false));
        subscribeAndGrant(channel, "a/#", handler);
        EmbeddedChannel reconnected = newChannelFor(client);
        reconnected.writeInbound(connAck(false));

        // WHEN
        reconnected.writeInbound(subAck(pendingSubscriptionIdFor("a/#"), MqttQoS.FAILURE.value()));

        // THEN - the next session-less reconnect tries it again
        assertThat(failures).containsExactly("a/# MqttSubscriptionFailedException");
        assertThat(client.getSubscriptions()).extracting(MqttSubscription::getTopic).containsExactly("a/#");
    }

    @Test
    void aResubscribeCutShortByADisconnectIsNotReported() {
        // GIVEN
        List<String> failures = new CopyOnWriteArrayList<>();
        MqttHandler handler = msg -> Futures.immediateVoidFuture();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        client.setCallback(new MqttClientCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void onSuccessfulReconnect() {
            }

            @Override
            public void onResubscribeFailed(String topicFilter, Throwable cause) {
                failures.add(topicFilter);
            }
        });
        channel.writeInbound(connAck(false));
        subscribeAndGrant(channel, "a/#", handler);
        EmbeddedChannel reconnected = newChannelFor(client);
        reconnected.writeInbound(connAck(false));

        // WHEN - the resubscribe is still in flight
        client.disconnect();

        // THEN - a closed channel is not a failure to report: a stop, or the next reconnect, deals with it
        assertThat(failures).isEmpty();
    }

    @Test
    void aConnectEndedByDisconnectBeforeItsConnackFailsAsClientIsDisconnected() {
        // GIVEN - the channel is up and the CONNECT is out, but no CONNACK has come
        client = new MqttClientImpl(testConfig(MqttVersion.MQTT_3_1_1), null, DIRECT_EXECUTOR);
        Promise<MqttConnectResult> connectFuture = ImmediateEventExecutor.INSTANCE.newPromise();
        channel = new EmbeddedChannel(new MqttPingHandler(client.getClientConfig().getTimeoutSeconds()),
                new MqttChannelHandler(client, connectFuture));
        client.setEventLoop(channel.eventLoop());
        client.setChannel(channel);

        // WHEN
        client.disconnect();
        channel.runPendingTasks();

        // THEN
        assertThat(connectFuture.isDone()).isTrue();
        assertThat(connectFuture.cause()).isInstanceOf(ChannelClosedException.class).hasMessage("Client is disconnected");
    }

    @Test
    void anOversizedPublishIsAckedWithUnspecifiedErrorUnderMqtt5() {
        // GIVEN
        channel = newChannel(null, MqttVersion.MQTT_5);
        channel.readOutbound(); // the CONNECT

        // WHEN - what the guard puts in the pipeline in place of an oversized PUBLISH
        channel.writeInbound(new MqttOversizedPublish("too/large", MqttQoS.AT_LEAST_ONCE, 9, 5_000));

        // THEN
        MqttMessage puback = channel.readOutbound();
        assertThat(messageId(puback)).isEqualTo(9);
        assertThat(reasonCode(puback)).isEqualTo((byte) 0x80);
    }

    @Test
    void anOversizedQoS2PublishIsAckedWithUnspecifiedErrorAndLeavesNoReceiveStateUnderMqtt5() {
        // GIVEN
        List<String> tooLarge = new CopyOnWriteArrayList<>();
        channel = newChannel(null, MqttVersion.MQTT_5);
        client.setCallback(recordingTooLarge(tooLarge));
        channel.readOutbound(); // the CONNECT

        // WHEN
        channel.writeInbound(new MqttOversizedPublish("too/large", MqttQoS.EXACTLY_ONCE, 9, 5_000));

        // THEN - under MQTT 5 the failure code ends the exchange: no PUBREL follows to release the id
        MqttMessage pubrec = channel.readOutbound();
        assertThat(pubrec.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBREC);
        assertThat(messageId(pubrec)).isEqualTo(9);
        assertThat(reasonCode(pubrec)).isEqualTo((byte) 0x80);
        assertThat(client.getQos2Received()).describedAs("receive state").doesNotContainKey(9);
        assertThat(tooLarge).containsExactly("too/large QoS 2 5000");
    }

    @Test
    void anOversizedQoS2PublishKeepsItsReceiveStateUntilThePubrelUnderMqtt311() {
        // GIVEN
        List<String> tooLarge = new CopyOnWriteArrayList<>();
        channel = newChannel(null, MqttVersion.MQTT_3_1_1);
        client.setCallback(recordingTooLarge(tooLarge));
        channel.readOutbound(); // the CONNECT

        // WHEN
        channel.writeInbound(new MqttOversizedPublish("too/large", MqttQoS.EXACTLY_ONCE, 9, 5_000));

        // THEN - its PUBREC carries no failure, so a PUBREL follows and a resend of it is answered from the entry
        assertThat(client.getQos2Received()).describedAs("receive state").containsKey(9);
        assertThat(written(channel)).containsExactly("PUBREC 9");
        assertThat(tooLarge).containsExactly("too/large QoS 2 5000");

        // WHEN
        channel.writeInbound(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(9)));

        // THEN
        assertThat(written(channel)).containsExactly("PUBCOMP 9");
        assertThat(client.getQos2Received()).describedAs("receive state").doesNotContainKey(9);
        assertThat(tooLarge).describedAs("reported once").hasSize(1);
    }

    /**
     * A QoS 2 publish as its first write leaves it: pending, and netty has consumed the caller's reference.
     */
    private MqttPendingPublish registerWrittenQos2Publish(ByteBuf payload) {
        MqttPendingPublish publish = client.registerPendingPublish("channel-handler/topic", payload, MqttQoS.EXACTLY_ONCE, false);
        publish.getMessage().release();
        return publish;
    }

    private static MqttClientCallback recordingTooLarge(List<String> tooLarge) {
        return new MqttClientCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void onSuccessfulReconnect() {
            }

            @Override
            public void onPublishTooLarge(String topic, MqttQoS qos, int remainingLength) {
                tooLarge.add(topic + " QoS " + qos.value() + " " + remainingLength);
            }
        };
    }

    private long inFlightPublishes() {
        return channel.pipeline().get(MqttChannelHandler.class).inFlightPublishes();
    }

    private EmbeddedChannel newChannel(MqttHandler defaultHandler) {
        return newChannel(defaultHandler, MqttVersion.MQTT_3_1);
    }

    private EmbeddedChannel newChannel(MqttHandler defaultHandler, MqttVersion protocolVersion) {
        return newChannel(testConfig(protocolVersion), defaultHandler, DIRECT_EXECUTOR);
    }

    /**
     * A channel with the inbound pipeline the client builds, minus the codec: MqttPingHandler retains each message it
     * passes on, and MqttChannelHandler handles it.
     */
    private EmbeddedChannel newChannel(MqttClientConfig clientConfig, MqttHandler defaultHandler, ListeningExecutor executor) {
        client = new MqttClientImpl(clientConfig, defaultHandler, executor);
        var embeddedChannel = new EmbeddedChannel(new MqttPingHandler(clientConfig.getTimeoutSeconds()),
                new MqttChannelHandler(client, ImmediateEventExecutor.INSTANCE.newPromise()));
        client.setEventLoop(embeddedChannel.eventLoop());
        return embeddedChannel;
    }

    static MqttClientConfig testConfig(MqttVersion protocolVersion) {
        var clientConfig = new MqttClientConfig();
        clientConfig.setProtocolVersion(protocolVersion);
        clientConfig.setOwnerId("Test[MqttChannelHandler]");
        clientConfig.setClientId("channel-handler");
        return clientConfig;
    }

    /** Another channel of the same client, as an automatic reconnect opens: the client's state carries over. */
    private EmbeddedChannel newChannelFor(MqttClientImpl sameClient) {
        EmbeddedChannel reconnected = new EmbeddedChannel(new MqttPingHandler(sameClient.getClientConfig().getTimeoutSeconds()),
                new MqttChannelHandler(sameClient, ImmediateEventExecutor.INSTANCE.newPromise()));
        otherChannels.add(reconnected);
        return reconnected;
    }

    /**
     * Closes {@code ch} and runs the client's close cleanup for it, as the close listener of a channel the client
     * connects does. The automatic reconnect is turned off first: the test opens the next channel itself.
     */
    private void closeAsTheClientSeesIt(EmbeddedChannel ch) {
        client.getClientConfig().setReconnect(false);
        ch.close();
        client.onChannelClosed(ch, "localhost", 1883, 0);
    }

    private static MqttConnAckMessage connAck(boolean sessionPresent) {
        return MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(sessionPresent)
                .build();
    }

    /** The topic filters of every SUBSCRIBE written to {@code ch} so far, in order; drains its outbound queue. */
    private static List<String> subscribedFilters(EmbeddedChannel ch) {
        List<String> filters = new ArrayList<>();
        for (Object out; (out = ch.readOutbound()) != null; ) {
            if (out instanceof MqttSubscribeMessage subscribe) {
                subscribe.payload().topicSubscriptions().forEach(s -> filters.add(s.topicName()));
            }
            ReferenceCountUtil.release(out);
        }
        return filters;
    }

    /** Each message written to {@code ch} so far, as its type and packet id; drains the outbound queue. */
    private static List<String> written(EmbeddedChannel ch) {
        List<String> written = new ArrayList<>();
        for (Object out; (out = ch.readOutbound()) != null; ) {
            MqttMessage message = (MqttMessage) out;
            written.add(message.variableHeader() instanceof MqttMessageIdVariableHeader id
                    ? message.fixedHeader().messageType() + " " + id.messageId()
                    : message.fixedHeader().messageType().toString());
            ReferenceCountUtil.release(out);
        }
        return written;
    }

    private int pendingSubscriptionIdFor(String filter) {
        return client.getPendingSubscriptions().entrySet().stream()
                .filter(e -> e.getValue().getTopic().equals(filter))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SUBSCRIBE pending for " + filter));
    }

    /** Subscribes {@code filter} and grants it, as if its SUBACK arrived on {@code ch}. */
    private void subscribeAndGrant(EmbeddedChannel ch, String filter, MqttHandler handler) {
        Future<MqttQoS> subscribed = client.on(filter, handler, MqttQoS.AT_LEAST_ONCE);
        ch.writeInbound(subAck(pendingSubscriptionIdFor(filter), MqttQoS.AT_LEAST_ONCE.value()));
        assertThat(subscribed.isSuccess()).describedAs("subscribe of %s granted", filter).isTrue();
    }

    private static MqttMessage pubrec(int packetId) {
        return new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(packetId));
    }

    private static MqttMessage pubrec(int packetId, byte reasonCode, MqttProperties properties) {
        return new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                new MqttPubReplyMessageVariableHeader(packetId, reasonCode, properties));
    }

    private static MqttPublishMessage qos2Publish(int packetId, boolean dup, ByteBuf payload) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, dup, MqttQoS.EXACTLY_ONCE, false, 0);
        return new MqttPublishMessage(fixedHeader, new MqttPublishVariableHeader("channel-handler/topic", packetId), payload);
    }

    private static MqttPublishMessage publish(String topic, MqttQoS qos, int packetId, boolean dup, ByteBuf payload) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, dup, qos, false, 0);
        return new MqttPublishMessage(fixedHeader, new MqttPublishVariableHeader(topic, packetId), payload);
    }

    private static ByteBuf payload(String content) {
        return Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
    }

    private static MqttHandler record(List<String> served, String name) {
        return msg -> {
            served.add(name);
            return Futures.immediateVoidFuture();
        };
    }

    private static MqttSubAckMessage subAck(int packetId, int code) {
        return new MqttSubAckMessage(new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(packetId), new MqttSubAckPayload(code));
    }

    private int onlyPendingSubscriptionId() {
        assertThat(client.getPendingSubscriptions()).describedAs("pending subscriptions").hasSize(1);
        return client.getPendingSubscriptions().keySet().iterator().next();
    }

    private static int messageId(MqttMessage message) {
        return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
    }

    private static byte reasonCode(MqttMessage reply) {
        assertThat(reply.variableHeader()).describedAs("a reply carrying a reason code").isInstanceOf(MqttPubReplyMessageVariableHeader.class);
        return ((MqttPubReplyMessageVariableHeader) reply.variableHeader()).reasonCode();
    }

    /**
     * What MQTT 3.x can carry: the packet id alone. A reply header whose code is 0x00 encodes to exactly that.
     */
    private static void assertCarriesNoReasonCode(MqttMessage reply) {
        if (reply.variableHeader() instanceof MqttPubReplyMessageVariableHeader header) {
            assertThat(header.reasonCode()).describedAs("reason code, which MQTT 3.x cannot carry").isZero();
        }
    }

}
