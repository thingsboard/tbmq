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
package org.thingsboard.mqtt.broker.common.data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = "properties")
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DevicePublishMsg {

    private String clientId;
    @JsonAlias("topic") // Map old "topic" field during deserialization. Can be removed in the future releases
    private String topicName;
    private Long time;
    private Integer qos;
    private Integer packetId;
    private PersistedPacketType packetType;
    private byte[] payload;
    @JsonIgnore
    private MqttProperties properties;
    private boolean isRetained;

    public DevicePublishMsg() {
        properties = new MqttProperties();
    }

    public UserProperties getUserProperties() {
        return UserProperties.newInstance(properties);
    }

    public Integer getMsgExpiryInterval() {
        MqttProperties.IntegerProperty property = (MqttProperties.IntegerProperty) this.getProperties().getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
        return property == null ? null : property.value();
    }

    public Integer getPayloadFormatIndicator() {
        MqttProperties.IntegerProperty property = (MqttProperties.IntegerProperty) this.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID);
        return property == null ? null : property.value();
    }

    public String getContentType() {
        MqttProperties.StringProperty property = (MqttProperties.StringProperty) this.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID);
        return property == null ? null : property.value();
    }

    public String getResponseTopic() {
        MqttProperties.StringProperty property = (MqttProperties.StringProperty) this.getProperties().getProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID);
        return property == null ? null : property.value();
    }

    public byte[] getCorrelationData() {
        MqttProperties.BinaryProperty property = (MqttProperties.BinaryProperty) this.getProperties().getProperty(BrokerConstants.CORRELATION_DATA_PROP_ID);
        return property == null ? null : property.value();
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getSubscriptionIds() {
        List<MqttProperties.IntegerProperty> properties = (List<MqttProperties.IntegerProperty>) this.getProperties().getProperties(BrokerConstants.SUBSCRIPTION_IDENTIFIER_PROP_ID);
        if (properties.isEmpty()) {
            return null;
        }
        ArrayList<Integer> subscriptionIds = new ArrayList<>(properties.size());
        properties.forEach(mqttProperty -> subscriptionIds.add(mqttProperty.value()));
        return subscriptionIds;
    }

    public void setUserProperties(UserProperties userProperties) {
        MqttProperties.UserProperties mqttUserProperties = UserProperties.mapToMqttUserProperties(userProperties);
        if (mqttUserProperties != null) {
            properties.add(mqttUserProperties);
        }
    }

    public void setMsgExpiryInterval(Integer msgExpiryInterval) {
        if (msgExpiryInterval != null) {
            properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, msgExpiryInterval));
        }
    }

    public void setPayloadFormatIndicator(Integer payloadFormatIndicator) {
        if (payloadFormatIndicator != null) {
            properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, payloadFormatIndicator));
        }
    }

    public void setContentType(String contentType) {
        if (contentType != null) {
            properties.add(new MqttProperties.StringProperty(BrokerConstants.CONTENT_TYPE_PROP_ID, contentType));
        }
    }

    public void setResponseTopic(String responseTopic) {
        if (responseTopic != null) {
            properties.add(new MqttProperties.StringProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID, responseTopic));
        }
    }

    public void setCorrelationData(byte[] correlationData) {
        if (correlationData != null) {
            properties.add(new MqttProperties.BinaryProperty(BrokerConstants.CORRELATION_DATA_PROP_ID, correlationData));
        }
    }

    public void setSubscriptionIds(List<Integer> subscriptionIds) {
        if (!CollectionUtils.isEmpty(subscriptionIds)) {
            subscriptionIds.forEach(subscriptionId -> {
                if (subscriptionId > 0) {
                    properties.add(new MqttProperties.IntegerProperty(BrokerConstants.SUBSCRIPTION_IDENTIFIER_PROP_ID, subscriptionId));
                }
            });
        }
    }

}
