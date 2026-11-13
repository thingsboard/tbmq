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
package org.thingsboard.mqtt.broker.standalone.device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Serves {@link DeviceMsgService} from the device queue store. The store is synchronous, so every returned stage is
 * already completed; a store failure completes it exceptionally. {@code failOnConflict} has no effect, as in the
 * Valkey implementation.
 */
@Service
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneDeviceMsgService implements DeviceMsgService {

    private static final String OK = "OK";

    private final DeviceMsgQueueStore store;

    @Override
    public CompletionStage<Integer> saveAndReturnPreviousPacketId(String clientId, List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict) {
        return complete(() -> {
            List<NewDeviceMsg> messages = devicePublishMessages.stream().map(DeviceMsgBodyCodec::toNew).toList();
            return store.append(List.of(new QueueAppend(clientId, messages))).get(0).previousPacketId();
        });
    }

    @Override
    public CompletionStage<List<DevicePublishMsg>> findPersistedMessages(String clientId) {
        return complete(() -> store.read(clientId, 0, Integer.MAX_VALUE).stream()
                .map(stored -> DeviceMsgBodyCodec.toMsg(clientId, stored))
                .toList());
    }

    @Override
    public CompletionStage<String> removePersistedMessages(String clientId) {
        return complete(() -> {
            store.removeQueue(clientId);
            return OK;
        });
    }

    @Override
    public CompletionStage<String> removePersistedMessage(String clientId, int packetId) {
        return complete(() -> {
            store.remove(clientId, packetId);
            return OK;
        });
    }

    @Override
    public CompletionStage<String> updatePacketReceived(String clientId, int packetId) {
        return complete(() -> {
            store.markPubRel(clientId, packetId);
            return OK;
        });
    }

    @Override
    public CompletionStage<Integer> getLastPacketId(String clientId) {
        return complete(() -> store.info(clientId).lastPacketId());
    }

    /**
     * Resets the packet id to 0 when the queue has no entries and does nothing otherwise ([Trello #654]: the cluster
     * edition deletes the counter under stored messages and reuses their ids).
     */
    @Override
    public CompletionStage<Long> removeLastPacketId(String clientId) {
        return complete(() -> store.resetPacketId(clientId, 0) ? 1L : 0L);
    }

    @Override
    public CompletionStage<String> saveLastPacketId(String clientId, int lastPacketId) {
        return complete(() -> {
            store.resetPacketId(clientId, lastPacketId);
            return OK;
        });
    }

    private static <T> CompletionStage<T> complete(Supplier<T> call) {
        try {
            return CompletableFuture.completedFuture(call.get());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

}
