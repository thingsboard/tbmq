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

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;

public class IncomingMessagesCtx {
    private final Set<Integer> awaitingQoS2PacketIds = Sets.newConcurrentHashSet();

    public void setAwaitingPacketIds(Set<Integer> awaitingPacketIds) {
        awaitingQoS2PacketIds.addAll(awaitingPacketIds);
    }

    public void await(int packetId) {
        awaitingQoS2PacketIds.add(packetId);
    }

    public boolean complete(int packetId) {
        return awaitingQoS2PacketIds.remove(packetId);
    }

    public boolean isAwaiting(int packetId) {
        return awaitingQoS2PacketIds.contains(packetId);
    }

    public Set<Integer> getAwaitingPacketIds() {
        return new HashSet<>(awaitingQoS2PacketIds);
    }
}
