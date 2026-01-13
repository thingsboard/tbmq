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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategy;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * AppMsgDeliveryStrategy implementation that buffers messages in the Netty channel
 * and flushes them after a configured number of messages.
 * <p>
 * This approach reduces system call overhead and improves throughput,
 * while still providing controlled flushing based on the `buffered-msg-count` setting.
 * <p>
 * Any remaining messages below the threshold are flushed explicitly
 * after the full batch is processed.
 */
@Component
@ConditionalOnProperty(prefix = "mqtt.persistent-session.app.persisted-messages", name = "write-and-flush", havingValue = "false")
@Slf4j
@RequiredArgsConstructor
public class AppMsgBufferedDeliveryStrategy implements AppMsgDeliveryStrategy {

    private final MqttMsgDeliveryService mqttMsgDeliveryService;
    private final ClientLogger clientLogger;

    @Value("${mqtt.persistent-session.app.persisted-messages.buffered-msg-count:10}")
    private int bufferedMsgCount;

    @Override
    public void process(ApplicationSubmitStrategy submitStrategy, ClientSessionCtx clientSessionCtx) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Buffered delivery of messages from processing ctx: {}", clientSessionCtx.getClientId(), submitStrategy.getOrderedMessages());
        }
        AtomicInteger counter = new AtomicInteger();
        submitStrategy.process(msg -> {
            int currentCount = counter.incrementAndGet();
            switch (msg.getPacketType()) {
                case PUBLISH ->
                        mqttMsgDeliveryService.sendPublishMsgToClientWithoutFlush(clientSessionCtx, msg.getPublishMsg());
                case PUBREL ->
                        mqttMsgDeliveryService.sendPubRelMsgToClientWithoutFlush(clientSessionCtx, msg.getPacketId());
            }
            if (currentCount % bufferedMsgCount == 0) {
                clientSessionCtx.getChannel().flush();
            }
            clientLogger.logEventWithDetails(clientSessionCtx.getClientId(), getClass(), ctx -> ctx
                    .msg("Delivered msg to App client")
                    .kv(StatsConstantNames.MSG_TYPE, msg.getPacketType())
                    .kv("msgId", msg.getPacketId())
            );
        });
        clientSessionCtx.getChannel().flush();
    }

}
