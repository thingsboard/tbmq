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
package org.thingsboard.mqtt.broker.dao.model.sql;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.dao.model.ModelConstants;
import org.thingsboard.mqtt.broker.dao.model.ToData;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Data
@EqualsAndHashCode
@Entity
@Table(name = ModelConstants.DEVICE_PUBLISH_MSG_COLUMN_FAMILY_NAME)
@IdClass(DevicePublishMsgCompositeKey.class)
public class DevicePublishMsgEntity implements ToData<DevicePublishMsg> {
    @Id
    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_CLIENT_ID_PROPERTY)
    private String clientId;

    @Id
    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY)
    private String topic;

    @Id
    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_SERIAL_NUMBER_PROPERTY)
    private Long serialNumber;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_TIME_PROPERTY)
    private Long time;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_PACKET_ID_PROPERTY)
    private Integer packetId;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY)
    private Integer qos;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY)
    private byte[] payload;

    public DevicePublishMsgEntity() {}

    public DevicePublishMsgEntity(DevicePublishMsg devicePublishMsg) {
        this.clientId = devicePublishMsg.getClientId();
        this.topic = devicePublishMsg.getTopic();
        this.serialNumber = devicePublishMsg.getSerialNumber();
        this.time = devicePublishMsg.getTime();
        this.packetId = devicePublishMsg.getPacketId();
        this.qos = devicePublishMsg.getQos();
        this.payload = devicePublishMsg.getPayload();
    }

    @Override
    public DevicePublishMsg toData() {
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topic(topic)
                .serialNumber(serialNumber)
                .time(time)
                .qos(qos)
                .payload(payload)
                .packetId(packetId)
                .build();
    }
}
