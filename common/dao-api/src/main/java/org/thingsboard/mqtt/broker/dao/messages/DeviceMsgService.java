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
package org.thingsboard.mqtt.broker.dao.messages;

import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DeviceMsgService {

    // TODO: failOnConflict, kafka rebalancing issue. Need to be tested.
    CompletionStage<Integer> saveAndReturnPreviousPacketId(String clientId, List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict);

    CompletionStage<List<DevicePublishMsg>> findPersistedMessages(String clientId);

    CompletionStage<String> removePersistedMessages(String clientId);

    CompletionStage<String> removePersistedMessage(String clientId, int packetId);

    CompletionStage<String> updatePacketReceived(String clientId, int packetId);

    CompletionStage<Integer> getLastPacketId(String clientId);

    CompletionStage<Long> removeLastPacketId(String clientId);

    CompletionStage<String> saveLastPacketId(String clientId, int lastPacketId);

}
