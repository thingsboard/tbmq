/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

public interface DeviceMsgService {

    // TODO: failOnConflict, kafka rebalancing issue. Need to be tested.
    int saveAndReturnPreviousPacketId(String clientId, List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict);

    List<DevicePublishMsg> findPersistedMessages(String clientId);

    void removePersistedMessages(String clientId);

    void removePersistedMessage(String clientId, int packetId);

    void updatePacketReceived(String clientId, int packetId);

    int getLastPacketId(String clientId);

    void removeLastPacketId(String clientId);

    void saveLastPacketId(String clientId, int lastPacketId);

    /**
     * Imports data from the CSV file.
     *
     * This method is used exclusively by the upgrade script to import data into the system.
     * It is marked as deprecated and is scheduled for removal in a future major release.
     *
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    void importFromCsvFile(Path filePath);

}
