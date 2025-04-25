/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.delivery;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BufferedMsgDeliveryService {

    private final PublishMsgDeliveryService publishMsgDeliveryService;

    @Value("${mqtt.buffered-msg-max-delay-ms:1000}")
    private long maxFlushDelayMs;

    @Value("${mqtt.write-and-flush:true}")
    private boolean writeAndFlush;
    @Value("${mqtt.buffered-msg-count:5}")
    private int bufferedMsgCount;
    @Value("${mqtt.persistent-session.device.persisted-messages.write-and-flush:true}")
    private boolean persistentWriteAndFlush;
    @Value("${mqtt.persistent-session.device.persisted-messages.buffered-msg-count:5}")
    private int persistentBufferedMsgCount;

    // Guava Cache to track last buffered time with auto-expiry
    private Cache<ChannelHandlerContext, Long> lastFlushTimestamps;

    @PostConstruct
    private void init() {
        this.lastFlushTimestamps = CacheBuilder.newBuilder()
                .expireAfterWrite(2 * maxFlushDelayMs, TimeUnit.MILLISECONDS)
                .build();
    }

    public void sendPublishMsgToRegularClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        sendPublishMsgToClient(sessionCtx, mqttPubMsg, writeAndFlush, bufferedMsgCount);
    }

    public void sendPublishMsgToDeviceClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        sendPublishMsgToClient(sessionCtx, mqttPubMsg, persistentWriteAndFlush, persistentBufferedMsgCount);
    }

    private void sendPublishMsgToClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg, boolean writeAndFlush, int bufferedMsgCount) {
        ChannelHandlerContext channel = sessionCtx.getChannel();

        if (writeAndFlush) {
            publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, mqttPubMsg);
            return;
        }
        publishMsgDeliveryService.sendPublishMsgWithoutFlushToClient(sessionCtx, mqttPubMsg);
        if (isFlushNeeded(mqttPubMsg.variableHeader().packetId(), bufferedMsgCount)) {
            sessionCtx.getChannel().flush();
            lastFlushTimestamps.invalidate(channel); // Reset after flush
        } else {
            lastFlushTimestamps.put(channel, System.currentTimeMillis());
        }
    }

    private boolean isFlushNeeded(int packetId, int bufferedMsgCount) {
        return packetId % bufferedMsgCount == 0;
    }

    // Periodic check to flush stale buffered channels
    @Scheduled(fixedRateString = "${mqtt.buffered-msg-check-interval-ms:500}")
    public void flushPendingBuffers() {
        long now = System.currentTimeMillis();
        lastFlushTimestamps.asMap().forEach((channel, lastBufferedTime) -> {
            if (channel.channel().isActive() && now - lastBufferedTime >= maxFlushDelayMs) {
                channel.flush();
                lastFlushTimestamps.invalidate(channel); // Reset after flushing
            }
        });
    }

}
