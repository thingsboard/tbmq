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
package org.thingsboard.mqtt.broker.dao.model.sql;

import io.netty.handler.codec.mqtt.MqttProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.model.ModelConstants;
import org.thingsboard.mqtt.broker.dao.model.ToData;

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
    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_SERIAL_NUMBER_PROPERTY)
    private Long serialNumber;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY)
    private String topic;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_TIME_PROPERTY)
    private Long time;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_PACKET_ID_PROPERTY)
    private Integer packetId;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_PACKET_TYPE_PROPERTY)
    private PersistedPacketType packetType;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY)
    private Integer qos;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY, columnDefinition = "BINARY")
    private byte[] payload;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_USER_PROPERTIES_PROPERTY)
    private String userProperties;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_RETAIN_PROPERTY)
    private boolean retain;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_EXPIRY_INTERVAL_PROPERTY)
    private Integer msgExpiryInterval;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_FORMAT_INDICATOR_PROPERTY)
    private Integer payloadFormatIndicator;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_CONTENT_TYPE_PROPERTY)
    private String contentType;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_RESPONSE_TOPIC_PROPERTY)
    private String responseTopic;

    @Column(name = ModelConstants.DEVICE_PUBLISH_MSG_CORRELATION_DATA_PROPERTY, columnDefinition = "BINARY")
    private byte[] correlationData;

    public DevicePublishMsgEntity() {
    }

    public DevicePublishMsgEntity(DevicePublishMsg devicePublishMsg) {
        this.clientId = devicePublishMsg.getClientId();
        this.topic = devicePublishMsg.getTopic();
        this.serialNumber = devicePublishMsg.getSerialNumber();
        this.time = devicePublishMsg.getTime();
        this.packetId = devicePublishMsg.getPacketId();
        this.packetType = devicePublishMsg.getPacketType();
        this.qos = devicePublishMsg.getQos();
        this.payload = devicePublishMsg.getPayload();
        this.userProperties = JacksonUtil.toString(UserProperties.newInstance(devicePublishMsg.getProperties()));
        this.retain = devicePublishMsg.isRetained();
        this.msgExpiryInterval = getMsgExpiryInterval(devicePublishMsg);
        this.payloadFormatIndicator = getPayloadFormatIndicator(devicePublishMsg);
        this.contentType = getContentType(devicePublishMsg);
        this.responseTopic = getResponseTopic(devicePublishMsg);
        this.correlationData = getCorrelationData(devicePublishMsg);
    }

    private Integer getMsgExpiryInterval(DevicePublishMsg devicePublishMsg) {
        MqttProperties.IntegerProperty property = (MqttProperties.IntegerProperty) devicePublishMsg.getProperties().getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
        return property == null ? null : property.value();
    }

    private Integer getPayloadFormatIndicator(DevicePublishMsg devicePublishMsg) {
        MqttProperties.IntegerProperty property = (MqttProperties.IntegerProperty) devicePublishMsg.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID);
        return property == null ? null : property.value();
    }

    private String getContentType(DevicePublishMsg devicePublishMsg) {
        MqttProperties.StringProperty property = (MqttProperties.StringProperty) devicePublishMsg.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID);
        return property == null ? null : property.value();
    }

    private String getResponseTopic(DevicePublishMsg devicePublishMsg) {
        MqttProperties.StringProperty property = (MqttProperties.StringProperty) devicePublishMsg.getProperties().getProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID);
        return property == null ? null : property.value();
    }

    private byte[] getCorrelationData(DevicePublishMsg devicePublishMsg) {
        MqttProperties.BinaryProperty property = (MqttProperties.BinaryProperty) devicePublishMsg.getProperties().getProperty(BrokerConstants.CORRELATION_DATA_PROP_ID);
        return property == null ? null : property.value();
    }

    @Override
    public DevicePublishMsg toData() {
        MqttProperties properties = UserProperties.mapToMqttProperties(JacksonUtil.fromString(userProperties, UserProperties.class));
        if (msgExpiryInterval != null) {
            properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, msgExpiryInterval));
        }
        if (payloadFormatIndicator != null) {
            properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, payloadFormatIndicator));
        }
        if (contentType != null) {
            properties.add(new MqttProperties.StringProperty(BrokerConstants.CONTENT_TYPE_PROP_ID, contentType));
        }
        if (responseTopic != null) {
            properties.add(new MqttProperties.StringProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID, responseTopic));
        }
        if (correlationData != null) {
            properties.add(new MqttProperties.BinaryProperty(BrokerConstants.CORRELATION_DATA_PROP_ID, correlationData));
        }
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topic(topic)
                .serialNumber(serialNumber)
                .time(time)
                .qos(qos)
                .payload(payload)
                .packetId(packetId)
                .packetType(packetType)
                .properties(properties)
                .isRetained(retain)
                .build();
    }
}
