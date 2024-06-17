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
package org.thingsboard.mqtt.broker.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;

@Data
@EqualsAndHashCode
@Entity
@Table(name = ModelConstants.DEVICE_SESSION_CTX_COLUMN_FAMILY_NAME)
public class DeviceSessionCtxEntity implements ToData<DeviceSessionCtx> {

    @Id
    @Column(name = ModelConstants.DEVICE_SESSION_CTX_CLIENT_ID_PROPERTY)
    private String clientId;

    @Column(name = ModelConstants.DEVICE_SESSION_CTX_LAST_UPDATED_PROPERTY)
    private long lastUpdatedTime;

    @Column(name = ModelConstants.DEVICE_SESSION_CTX_LAST_SERIAL_NUMBER_PROPERTY)
    private Long lastSerialNumber;

    @Column(name = ModelConstants.DEVICE_SESSION_CTX_LAST_PACKET_ID_PROPERTY)
    private Integer lastPacketId;

    public DeviceSessionCtxEntity() {
    }

    public DeviceSessionCtxEntity(DeviceSessionCtx deviceSessionCtx) {
        this.clientId = deviceSessionCtx.getClientId();
        this.lastUpdatedTime = deviceSessionCtx.getLastUpdatedTime();
        this.lastSerialNumber = deviceSessionCtx.getLastSerialNumber();
        this.lastPacketId = deviceSessionCtx.getLastPacketId();
    }

    @Override
    public DeviceSessionCtx toData() {
        return DeviceSessionCtx.builder()
                .clientId(clientId)
                .lastUpdatedTime(lastUpdatedTime)
                .lastSerialNumber(lastSerialNumber)
                .lastPacketId(lastPacketId)
                .build();
    }
}
