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
package org.thingsboard.mqtt.broker.session;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class AwaitingPubRelPacketsCtx {
    private final ConcurrentMap<Integer, QoS2PubRelPacketInfo> awaitingForQoS2PubRelPackets = new ConcurrentHashMap<>();

    public void loadPersistedPackets(Set<Integer> awaitingPacketIds) {
        Map<Integer, QoS2PubRelPacketInfo> newAwaitingPackets = awaitingPacketIds.stream()
                .collect(Collectors.toMap(Function.identity(), id -> new QoS2PubRelPacketInfo(id, true)));
        awaitingForQoS2PubRelPackets.putAll(newAwaitingPackets);
    }

    public void await(String clientId, int packetId) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Adding packet, awaitingForQoS2PubRelPackets size: {}", clientId, awaitingForQoS2PubRelPackets.size());
        }
        awaitingForQoS2PubRelPackets.put(packetId, new QoS2PubRelPacketInfo(packetId));
    }

    public boolean complete(String clientId, int packetId) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Removing packet, awaitingForQoS2PubRelPackets size: {}", clientId, awaitingForQoS2PubRelPackets.size());
        }
        return awaitingForQoS2PubRelPackets.remove(packetId) != null;
    }

    public QoS2PubRelPacketInfo getAwaitingPacket(int packetId) {
        return awaitingForQoS2PubRelPackets.get(packetId);
    }

    public Collection<QoS2PubRelPacketInfo> getAwaitingPackets() {
        return awaitingForQoS2PubRelPackets.values();
    }

    @Getter
    @EqualsAndHashCode
    public static class QoS2PubRelPacketInfo {
        private final int packetId;
        @Setter
        private volatile boolean persisted = false;

        public QoS2PubRelPacketInfo(int packetId) {
            this.packetId = packetId;
        }

        public QoS2PubRelPacketInfo(int packetId, boolean isPersisted) {
            this.packetId = packetId;
            this.persisted = isPersisted;
        }
    }
}
