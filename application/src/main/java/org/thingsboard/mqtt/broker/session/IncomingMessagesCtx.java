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
package org.thingsboard.mqtt.broker.session;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IncomingMessagesCtx {
    private final ConcurrentMap<Integer, QoS2PacketInfo> awaitingQoS2Packets = new ConcurrentHashMap<>();

    public void loadPersistedPackets(Set<Integer> awaitingPacketIds) {
        Map<Integer, QoS2PacketInfo> newAwaitingPackets = awaitingPacketIds.stream()
                .collect(Collectors.toMap(Function.identity(), id -> new QoS2PacketInfo(id, true)));
        awaitingQoS2Packets.putAll(newAwaitingPackets);
    }

    public void await(int packetId) {
        awaitingQoS2Packets.put(packetId, new QoS2PacketInfo(packetId));
    }

    public boolean complete(int packetId) {
        return awaitingQoS2Packets.remove(packetId) != null;
    }

    public QoS2PacketInfo getAwaitingPacket(int packetId) {
        return awaitingQoS2Packets.get(packetId);
    }

    public Collection<QoS2PacketInfo> getAwaitingPacketIds() {
        return awaitingQoS2Packets.values();
    }

    @Getter
    @EqualsAndHashCode
    public static class QoS2PacketInfo {
        private final int packetId;
        @Setter
        private volatile boolean persisted = false;

        public QoS2PacketInfo(int packetId) {
            this.packetId = packetId;
        }

        public QoS2PacketInfo(int packetId, boolean isPersisted) {
            this.packetId = packetId;
            this.persisted = isPersisted;
        }
    }
}
