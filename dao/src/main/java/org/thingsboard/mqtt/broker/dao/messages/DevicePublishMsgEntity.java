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

import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.csv.CSVRecord;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.model.ToData;

import java.util.Objects;

@Data
@Slf4j
@EqualsAndHashCode
public class DevicePublishMsgEntity implements ToData<DevicePublishMsg> {

    private String clientId;
    private String topic;
    private Long time;
    private Integer packetId;
    private PersistedPacketType packetType;
    private Integer qos;
    private byte[] payload;
    private String userProperties;
    private boolean retain;
    private Integer msgExpiryInterval;
    private Integer payloadFormatIndicator;
    private String contentType;
    private String responseTopic;
    private byte[] correlationData;

    public DevicePublishMsgEntity() {

    }

    public DevicePublishMsgEntity(DevicePublishMsg devicePublishMsg, int defaultTtl) {
        this.clientId = devicePublishMsg.getClientId();
        this.topic = devicePublishMsg.getTopic();
        this.time = devicePublishMsg.getTime();
        this.packetId = devicePublishMsg.getPacketId();
        this.packetType = devicePublishMsg.getPacketType();
        this.qos = devicePublishMsg.getQos();
        this.payload = devicePublishMsg.getPayload();
        this.userProperties = JacksonUtil.toString(UserProperties.newInstance(devicePublishMsg.getProperties()));
        this.retain = devicePublishMsg.isRetained();
        this.msgExpiryInterval = getMsgExpiryInterval(devicePublishMsg, defaultTtl);
        this.payloadFormatIndicator = getPayloadFormatIndicator(devicePublishMsg);
        this.contentType = getContentType(devicePublishMsg);
        this.responseTopic = getResponseTopic(devicePublishMsg);
        this.correlationData = getCorrelationData(devicePublishMsg);
    }

    private Integer getMsgExpiryInterval(DevicePublishMsg devicePublishMsg, int defaultTtl) {
        MqttProperties.IntegerProperty property = (MqttProperties.IntegerProperty) devicePublishMsg.getProperties().getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
        return property == null ? defaultTtl : property.value();
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

    public static DevicePublishMsg fromBytes(byte[] bytes) {
        return Objects.requireNonNull(JacksonUtil.fromBytes(bytes, DevicePublishMsgEntity.class)).toData();
    }

    @Deprecated(forRemoval = true, since = "1.3.1")
    public static DevicePublishMsgEntity fromCsvRecord(CSVRecord record, int defaultTtl) throws DecoderException {
        var entity = new DevicePublishMsgEntity();
        // non-null fields
        String clientId = record.get("client_id");
        entity.setClientId(clientId);
        entity.setTopic(record.get("topic"));
        entity.setTime(Long.parseLong(record.get("time")));

        entity.setPacketId(Integer.parseInt(record.get("packet_id")));
        entity.setPacketType(PersistedPacketType.valueOf(record.get("packet_type")));

        // non-null fields
        entity.setQos(Integer.parseInt(record.get("qos")));
        entity.setPayload(getPayload(record, clientId));

        entity.setUserProperties(getUserProperties(record));
        entity.setRetain(isRetain(record));
        entity.setMsgExpiryInterval(getMsgExpiryInterval(record, defaultTtl));
        entity.setPayloadFormatIndicator(getPayloadFormatIndicator(record));
        entity.setContentType(getContentType(record));
        entity.setResponseTopic(getResponseTopic(record));
        entity.setCorrelationData(getCorrelationData(record, clientId));
        return entity;
    }

    private static byte[] getPayload(CSVRecord record, String clientId) {
        try {
            return Hex.decodeHex(record.get("payload").substring(2)); // substring \x
        } catch (DecoderException e) {
            log.error("Failed to decode payload for clientId: {}", clientId, e);
            return null;
        }
    }

    private static String getUserProperties(CSVRecord record) {
        String userProperties = record.get("user_properties");
        return StringUtils.isNotBlank(userProperties) ? userProperties : null;
    }

    private static boolean isRetain(CSVRecord record) {
        return "t".equalsIgnoreCase(record.get("retain"));
    }

    private static int getMsgExpiryInterval(CSVRecord record, int defaultTtl) {
        String msgExpiryInterval = record.get("msg_expiry_interval");
        return StringUtils.isNotBlank(msgExpiryInterval) ? Integer.parseInt(msgExpiryInterval) : defaultTtl;
    }

    private static Integer getPayloadFormatIndicator(CSVRecord record) {
        String payloadFormatIndicator = record.get("payload_format_indicator");
        return StringUtils.isNotBlank(payloadFormatIndicator) ? Integer.parseInt(payloadFormatIndicator) : null;
    }

    private static String getContentType(CSVRecord record) {
        String contentType = record.get("content_type");
        return StringUtils.isNotBlank(contentType) ? contentType : null;
    }

    private static String getResponseTopic(CSVRecord record) {
        String responseTopic = record.get("response_topic");
        return StringUtils.isNotBlank(responseTopic) ? responseTopic : null;
    }

    private static byte[] getCorrelationData(CSVRecord record, String clientId) {
        String correlationData = record.get("correlation_data");
        if (StringUtils.isBlank(correlationData)) {
            log.debug("Correlation data is missing for clientId: {}", clientId);
            return null;
        }
        try {
            return Hex.decodeHex(correlationData.substring(2)); // substring \x
        } catch (DecoderException e) {
            log.error("Failed to parse correlation data for clientId: {}", clientId, e);
            return null;
        }
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
