/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.keepalive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.MqttException;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class DefaultKeepAliveService implements KeepAliveService {
    private static final int CLEARED_KEEP_ALIVE_VALUE = -1;

    private final Map<UUID, KeepAliveInfo> keepAliveInfoMap = new ConcurrentHashMap<>();

    @Scheduled(fixedRateString = "${application.mqtt.keep-alive.monitoring-delay-ms}")
    private void printInMemoryStats() {
        for (Iterator<Map.Entry<UUID, KeepAliveInfo>> it = keepAliveInfoMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, KeepAliveInfo> entry = it.next();
            KeepAliveInfo keepAliveInfo = entry.getValue();
            long lastPacketTime = keepAliveInfo.getLastPacketTime().get();
            if (isInactive(keepAliveInfo.getKeepAliveSeconds(), lastPacketTime)
                    && keepAliveInfo.getLastPacketTime().compareAndSet(lastPacketTime, CLEARED_KEEP_ALIVE_VALUE)) {
                it.remove();
                log.debug("[{}] Closing session for inactivity", entry.getKey());
                keepAliveInfo.getCloseSession().run();
            }
        }
    }

    private boolean isInactive(int keepAliveSeconds, long lastPacketTime) {
        long now = System.currentTimeMillis();
        long actualKeepAliveMs = (long) (TimeUnit.SECONDS.toMillis(keepAliveSeconds) * 1.5);
        return lastPacketTime + actualKeepAliveMs < now;
    }

    @Override
    public void registerSession(UUID sessionId, int keepAliveSeconds, Runnable closeSession) throws MqttException {
        log.trace("[{}] Registering keep-alive session for {} seconds", sessionId, keepAliveSeconds);
        keepAliveInfoMap.put(sessionId, new KeepAliveInfo(keepAliveSeconds, closeSession,
                new AtomicLong(System.currentTimeMillis())));
    }

    @Override
    public void unregisterSession(UUID sessionId) {
        log.trace("[{}] Unregistering keep-alive session", sessionId);
        keepAliveInfoMap.remove(sessionId);
    }

    @Override
    public void acknowledgeControlPacket(UUID sessionId) throws MqttException {
        log.trace("[{}] Acknowledging control packet for session", sessionId);
        KeepAliveInfo keepAliveInfo = keepAliveInfoMap.get(sessionId);
        if (keepAliveInfo == null) {
            log.warn("[{}] Cannot find keepAliveInfo", sessionId);
            throw new MqttException("Cannot find KeepAliveInfo for session " + sessionId);
        }
        long lastPacketTime = keepAliveInfo.getLastPacketTime().get();
        if (lastPacketTime == CLEARED_KEEP_ALIVE_VALUE
                || !keepAliveInfo.getLastPacketTime().compareAndSet(lastPacketTime, System.currentTimeMillis())) {
            log.warn("[{}] LastPacketTime is already cleared", sessionId);
            throw new MqttException("LastPacketTime is already cleared for session " + sessionId);
        }
    }

    @AllArgsConstructor
    @Getter
    private static class KeepAliveInfo {
        private final int keepAliveSeconds;
        private final Runnable closeSession;
        private final AtomicLong lastPacketTime;
    }
}
