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
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class BufferedMsgDeliveryServiceImpl implements BufferedMsgDeliveryService {

    private final @Lazy PublishMsgDeliveryService publishMsgDeliveryService;

    @Value("${mqtt.write-and-flush:true}")
    private boolean writeAndFlush;
    @Value("${mqtt.buffered-msg-count:5}")
    private int bufferedMsgCount;
    @Value("${mqtt.persistent-session.device.persisted-messages.write-and-flush:true}")
    private boolean persistentWriteAndFlush;
    @Value("${mqtt.persistent-session.device.persisted-messages.buffered-msg-count:5}")
    private int persistentBufferedMsgCount;

    @Value("${mqtt.buffered-delivery.flush-scheduler-interval-ms:100}")
    private long flushSchedulerIntervalMs;
    @Value("${mqtt.buffered-delivery.idle-flush-timeout-ms:1000}")
    private long idleFlushTimeoutMs;
    @Value("${mqtt.buffered-delivery.session-flush-cache-expiration-ms:1000}")
    private long sessionFlushCacheExpirationMs;
    @Value("${mqtt.buffered-delivery.session-flush-cache-max-size:10000}")
    private int sessionFlushCacheMaxSize;

    private Cache<UUID, SessionFlushState> cache;
    private ScheduledExecutorService flushScheduler;

    @PostConstruct
    public void init() {
        if (!writeAndFlush || !persistentWriteAndFlush) {
            cache = CacheBuilder.newBuilder()
                    .expireAfterAccess(sessionFlushCacheExpirationMs, TimeUnit.MILLISECONDS)
                    .maximumSize(sessionFlushCacheMaxSize)
                    .removalListener(getCacheRemovalListener())
                    .build();

            flushScheduler = ThingsBoardExecutors.newSingleScheduledThreadPool("buff-delivery-scheduler");
            flushScheduler.scheduleAtFixedRate(() -> flushPendingBuffers(false), flushSchedulerIntervalMs, flushSchedulerIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (flushScheduler != null) {
            log.info("Flushing all buffers before shutdown");
            flushPendingBuffers(true);
            ThingsBoardExecutors.shutdownAndAwaitTermination(flushScheduler, "Buffered delivery scheduler");
        }
    }

    @Override
    public void sendPublishMsgToRegularClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        sendPublishMsgToClient(sessionCtx, mqttPubMsg, writeAndFlush, bufferedMsgCount);
    }

    @Override
    public void sendPublishMsgToDeviceClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        sendPublishMsgToClient(sessionCtx, mqttPubMsg, persistentWriteAndFlush, persistentBufferedMsgCount);
    }

    private void sendPublishMsgToClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg, boolean writeAndFlush, int bufferedMsgCount) {
        if (writeAndFlush) {
            publishMsgDeliveryService.doSendPublishMsgToClient(sessionCtx, mqttPubMsg);
            return;
        }

        SessionFlushState state;
        try {
            state = cache.get(sessionCtx.getSessionId(), () -> new SessionFlushState(System.currentTimeMillis(), new AtomicInteger(), sessionCtx));
        } catch (ExecutionException e) {
            log.warn("[{}] Unexpected exception while loading SessionFlushState", sessionCtx.getClientId(), e);
            publishMsgDeliveryService.doSendPublishMsgToClient(sessionCtx, mqttPubMsg);
            return;
        }

        publishMsgDeliveryService.doSendPublishMsgToClientWithoutFlush(sessionCtx, mqttPubMsg);
        if (isFlushNeeded(state, bufferedMsgCount)) {
            doFlush(sessionCtx.getClientId(), state, System.currentTimeMillis());
        }
    }

    private boolean isFlushNeeded(SessionFlushState state, int bufferedMsgCount) {
        return state.incrementAndGetBufferedCount() % bufferedMsgCount == 0;
    }

    private void doFlush(String clientId, SessionFlushState state, long now) {
        try {
            state.getCtx().getChannel().executor().execute(() -> {
                state.getCtx().getChannel().flush();
                state.resetBufferedCount();
                state.setLastFlushTime(now);
            });
        } catch (Exception e) {
            log.warn("[{}] Failed to flush client session buffer", clientId, e);
        }
    }

    RemovalListener<UUID, SessionFlushState> getCacheRemovalListener() {
        return notification -> {
            if (notification.getKey() != null && notification.getValue() != null && notification.getValue().getBufferedCount() > 0) {
                try {
                    ChannelHandlerContext channel = notification.getValue().getCtx().getChannel();
                    channel.executor().execute(channel::flush);

                    log.debug("[{}] Flushed due to cache eviction ({}): {} buffered messages",
                            notification.getValue().getClientId(), notification.getCause(), notification.getValue().getBufferedCount());
                    if (notification.getCause().equals(RemovalCause.SIZE)) {
                        log.info("[{}] Client session was evicted due to cache size limit. " +
                                "If you see this message often, consider increasing the maximum cache size {}", notification.getValue().getClientId(), sessionFlushCacheMaxSize);
                    }
                } catch (Exception e) {
                    log.warn("[{}] Exception during cache eviction flush", notification.getValue().getClientId(), e);
                }
            }
        };
    }

    void flushPendingBuffers(boolean forceFlush) {
        try {
            final long now = System.currentTimeMillis();
            cache.asMap().forEach((sessionId, state) -> {
                if (forceFlush) {
                    doFlush(state.getClientId(), state, now);
                } else {
                    if ((now - state.getLastFlushTime()) >= idleFlushTimeoutMs && state.getBufferedCount() > 0) {
                        doFlush(state.getClientId(), state, now);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Exception during periodic buffer flush", e);
        }
    }


    @Data
    @AllArgsConstructor
    static class SessionFlushState {

        private long lastFlushTime;
        private AtomicInteger bufferedCounter;
        private ClientSessionCtx ctx;

        public int getBufferedCount() {
            return bufferedCounter.get();
        }

        public int incrementAndGetBufferedCount() {
            return bufferedCounter.incrementAndGet();
        }

        public void resetBufferedCount() {
            bufferedCounter.set(0);
        }

        public String getClientId() {
            return ctx.getClientId();
        }
    }
}
