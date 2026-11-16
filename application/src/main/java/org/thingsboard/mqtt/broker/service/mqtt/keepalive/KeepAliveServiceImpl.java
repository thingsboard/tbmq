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
package org.thingsboard.mqtt.broker.service.mqtt.keepalive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.processing.backpressure.KeepAliveProbe;
import org.thingsboard.mqtt.broker.service.processing.backpressure.PublishBackpressure;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeepAliveServiceImpl implements KeepAliveService {

    private static final int CLEARED_KEEP_ALIVE_VALUE = -1;

    private final Map<UUID, KeepAliveInfo> keepAliveInfoMap = new ConcurrentHashMap<>();

    private final ClientMqttActorManager clientMqttActorManager;
    private final PublishBackpressure publishBackpressure;

    @Scheduled(fixedRateString = "${mqtt.keep-alive.monitoring-delay-ms}")
    public void processKeepAlive() {
        for (Map.Entry<UUID, KeepAliveInfo> entry : keepAliveInfoMap.entrySet()) {
            UUID sessionId = entry.getKey();
            KeepAliveInfo keepAliveInfo = entry.getValue();
            long lastPacketTime = keepAliveInfo.getLastPacketTime().get();
            if (!isInactive(keepAliveInfo.getKeepAliveSeconds(), lastPacketTime)) {
                readPausedConnectionIfDue(sessionId, keepAliveInfo.getKeepAliveSeconds());
                continue;
            }
            // a paused connection does not read what the client sent, so the session may be alive although it is silent
            KeepAliveProbe probe = publishBackpressure.probe(sessionId, lastPacketTime);
            if (probe == KeepAliveProbe.ALIVE) {
                // a lost CAS means the session was refreshed or cleared meanwhile
                keepAliveInfo.getLastPacketTime().compareAndSet(lastPacketTime, System.currentTimeMillis());
                continue;
            }
            if (probe == KeepAliveProbe.WAIT) {
                continue;
            }
            if (keepAliveInfo.getLastPacketTime().compareAndSet(lastPacketTime, CLEARED_KEEP_ALIVE_VALUE)) {
                keepAliveInfoMap.remove(sessionId);
                log.debug("[{}][{}] Closing session for inactivity, last active time - {}, keep alive seconds - {}",
                        keepAliveInfo.getClientId(), sessionId, lastPacketTime, keepAliveInfo.getKeepAliveSeconds());
                clientMqttActorManager.disconnect(keepAliveInfo.getClientId(), new MqttDisconnectMsg(sessionId,
                        new DisconnectReason(DisconnectReasonType.ON_KEEP_ALIVE, "Client was inactive too long")));
            }
        }
    }

    /**
     * A paused connection does not read what the client sent, a PINGREQ included, and a client that waits a keep-alive
     * for its PINGRESP gives up. Read once every half keep-alive. With the reads off, each read takes one
     * buffer: a PINGREQ queued behind publishes is reached only as these reads work through them, so it can be
     * answered later than half a keep-alive. A keep-alive of 0 turns the keep-alive off, and these reads with it.
     */
    private void readPausedConnectionIfDue(UUID sessionId, int keepAliveSeconds) {
        if (keepAliveSeconds > 0) {
            publishBackpressure.readIfDue(sessionId, pausedReadIntervalMs(keepAliveSeconds));
        }
    }

    long pausedReadIntervalMs(int keepAliveSeconds) {
        return TimeUnit.SECONDS.toMillis(keepAliveSeconds) / 2;
    }

    boolean isInactive(int keepAliveSeconds, long lastPacketTime) {
        // A Keep Alive value of 0 has the effect of turning off the Keep Alive mechanism
        if (keepAliveSeconds == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long actualKeepAliveMs = (long) (TimeUnit.SECONDS.toMillis(keepAliveSeconds) * 1.5);
        return lastPacketTime + actualKeepAliveMs < now;
    }

    @Override
    public void registerSession(String clientId, UUID sessionId, int keepAliveSeconds) throws MqttException {
        log.trace("[{}] Registering keep-alive session for {} seconds", sessionId, keepAliveSeconds);
        keepAliveInfoMap.put(sessionId, new KeepAliveInfo(clientId, keepAliveSeconds,
                new AtomicLong(System.currentTimeMillis())));
    }

    @Override
    public void unregisterSession(UUID sessionId) {
        log.trace("[{}] Unregistering keep-alive session", sessionId);
        keepAliveInfoMap.remove(sessionId);
    }

    @Override
    public void acknowledgeControlPacket(UUID sessionId) throws MqttException {
        KeepAliveInfo keepAliveInfo = keepAliveInfoMap.get(sessionId);
        if (keepAliveInfo == null) {
            log.warn("[{}] Cannot find keepAliveInfo", sessionId);
            throw new MqttException("Cannot find KeepAliveInfo for session " + sessionId);
        }
        // not a compare-and-set against a value read before: the keep-alive thread may refresh the time meanwhile
        // (a probe that found the client alive), which is no reason to refuse the packet
        long now = System.currentTimeMillis();
        long previous = keepAliveInfo.getLastPacketTime().getAndUpdate(v -> v == CLEARED_KEEP_ALIVE_VALUE ? v : now);
        if (previous == CLEARED_KEEP_ALIVE_VALUE) {
            log.warn("[{}] LastPacketTime is already cleared", sessionId);
            throw new MqttException("LastPacketTime is already cleared for session " + sessionId);
        }
    }

    int getKeepAliveInfoSize() {
        return keepAliveInfoMap.size();
    }

    @AllArgsConstructor
    @Getter
    private static class KeepAliveInfo {
        private final String clientId;
        private final int keepAliveSeconds;
        private final AtomicLong lastPacketTime;
    }
}
