/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.retransmission;

import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
@Slf4j
public class RetransmissionHandler<T extends MqttMessage> {

    private final PendingOperation pendingOperation;
    private final int retransmissionInitDelay;
    private final int retransmissionPeriod;

    private volatile boolean stopped;
    private ScheduledFuture<?> timer;
    private int timeout;
    @Setter
    private BiConsumer<MqttFixedHeader, T> handler;
    @Setter
    private T originalMessage;

    void start(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw new NullPointerException("Retransmission scheduler is null");
        }
        if (this.handler == null) {
            throw new NullPointerException("Retransmission handler is null");
        }
        this.timeout = retransmissionInitDelay;
        this.startTimer(scheduler);
    }

    private void startTimer(ScheduledExecutorService scheduler) {
        if (stopped || pendingOperation.isCanceled()) {
            return;
        }
        this.timer = scheduler.schedule(() -> {
            if (stopped || pendingOperation.isCanceled()) {
                return;
            }
            this.timeout += retransmissionPeriod;
            boolean isDup = this.originalMessage.fixedHeader().isDup();
            if (this.originalMessage.fixedHeader().messageType() == MqttMessageType.PUBLISH &&
                    this.originalMessage.fixedHeader().qosLevel() != MqttQoS.AT_MOST_ONCE) {
                isDup = true;
            }
            MqttFixedHeader fixedHeader = newMqttFixedHeader(isDup);
            log.debug("[{}] Resending msg...", this.originalMessage.fixedHeader().messageType());
            handler.accept(fixedHeader, originalMessage);
            startTimer(scheduler);
        }, timeout, TimeUnit.SECONDS);
    }

    private MqttFixedHeader newMqttFixedHeader(boolean isDup) {
        return new MqttFixedHeader(
                this.originalMessage.fixedHeader().messageType(),
                isDup,
                this.originalMessage.fixedHeader().qosLevel(),
                this.originalMessage.fixedHeader().isRetain(),
                this.originalMessage.fixedHeader().remainingLength());
    }

    void stop() {
        stopped = true;
        if (this.timer != null) {
            this.timer.cancel(true);
        }
    }
}
