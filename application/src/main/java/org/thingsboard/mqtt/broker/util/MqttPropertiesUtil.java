/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.util;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;

import java.util.concurrent.TimeUnit;

public class MqttPropertiesUtil {

    /**
     * Expiry helpful methods
     */

    public static boolean isRetainedMsgExpired(RetainedMsg retainedMsg, long currentTs) {
        MqttProperties.IntegerProperty property = getPubExpiryIntervalProperty(retainedMsg);
        if (property != null && property.value() > 0) {
            int messageExpiryInterval = property.value();
            long createdTime = retainedMsg.getCreatedTime();
            return isMsgExpired(createdTime, messageExpiryInterval, currentTs);
        }
        return false;
    }

    private static boolean isMsgExpired(long createdTime, int messageExpiryInterval, long currentTs) {
        return createdTime + TimeUnit.SECONDS.toMillis(messageExpiryInterval) < currentTs;
    }

    public static boolean isRetainedMsgNotExpired(RetainedMsg retainedMsg, long currentTs) {
        return !isRetainedMsgExpired(retainedMsg, currentTs);
    }

    /**
     * Get properties
     */

    public static MqttProperties.IntegerProperty getPubExpiryIntervalProperty(RetainedMsg retainedMsg) {
        return getPubExpiryIntervalProperty(retainedMsg.getProperties());
    }

    public static MqttProperties.IntegerProperty getPubExpiryIntervalProperty(PublishMsg publishMsg) {
        return getPubExpiryIntervalProperty(publishMsg.getProperties());
    }

    public static MqttProperties.IntegerProperty getPubExpiryIntervalProperty(DevicePublishMsg devicePublishMsg) {
        return getPubExpiryIntervalProperty(devicePublishMsg.getProperties());
    }

    public static MqttProperties.IntegerProperty getPubExpiryIntervalProperty(MqttProperties properties) {
        return getIntegerProperty(properties, BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
    }

    public static MqttProperties.IntegerProperty getTopicAliasMaxProperty(MqttProperties properties) {
        return getIntegerProperty(properties, BrokerConstants.TOPIC_ALIAS_MAX_PROP_ID);
    }

    public static int getRequestResponseInfoPropertyValue(MqttProperties properties) {
        MqttProperties.IntegerProperty requestResponseInfoProp = getIntegerProperty(properties, BrokerConstants.REQUEST_RESPONSE_INFO_PROP_ID);
        return requestResponseInfoProp == null ? 0 : requestResponseInfoProp.value();
    }

    public static MqttProperties.MqttProperty getSubscriptionIdProperty(MqttProperties properties) {
        return properties.getProperty(BrokerConstants.SUBSCRIPTION_IDENTIFIER_PROP_ID);
    }

    public static MqttProperties.IntegerProperty getSessionExpiryIntervalProperty(MqttProperties properties) {
        return getIntegerProperty(properties, BrokerConstants.SESSION_EXPIRY_INTERVAL_PROP_ID);
    }

    public static MqttProperties.IntegerProperty getTopicAliasProperty(MqttProperties properties) {
        return getIntegerProperty(properties, BrokerConstants.TOPIC_ALIAS_PROP_ID);
    }

    public static MqttProperties.IntegerProperty getPayloadFormatProperty(MqttProperties mqttProperties) {
        return getIntegerProperty(mqttProperties, BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID);
    }

    public static MqttProperties.StringProperty getContentTypeProperty(MqttProperties mqttProperties) {
        return (MqttProperties.StringProperty) mqttProperties.getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID);
    }

    public static MqttProperties.BinaryProperty getCorrelationDataProperty(MqttProperties mqttProperties) {
        return (MqttProperties.BinaryProperty) mqttProperties.getProperty(BrokerConstants.CORRELATION_DATA_PROP_ID);
    }

    public static MqttProperties.StringProperty getResponseTopicProperty(MqttProperties mqttProperties) {
        return (MqttProperties.StringProperty) mqttProperties.getProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID);
    }

    public static String getResponseTopicValue(MqttProperties mqttProperties) {
        MqttProperties.StringProperty property = getResponseTopicProperty(mqttProperties);
        return property == null ? null : property.value();
    }

    public static byte[] getCorrelationDataValue(MqttProperties mqttProperties) {
        MqttProperties.BinaryProperty property = getCorrelationDataProperty(mqttProperties);
        return property == null ? null : property.value();
    }

    private static MqttProperties.IntegerProperty getIntegerProperty(MqttProperties properties, int propertyId) {
        MqttProperties.MqttProperty property = properties.getProperty(propertyId);
        return property != null ? (MqttProperties.IntegerProperty) property : null;
    }

    public static MqttProperties.UserProperties getUserProperties(PublishMsg publishMsg) {
        return getUserProperties(publishMsg.getProperties());
    }

    public static MqttProperties.UserProperties getUserProperties(MqttProperties properties) {
        return (MqttProperties.UserProperties) properties.getProperty(BrokerConstants.USER_PROPERTY_PROP_ID);
    }

    /**
     * Add properties
     */

    public static void addTopicAliasToProps(MqttProperties properties, int topicAlias) {
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID, topicAlias));
    }

    public static void addPayloadFormatIndicatorToProps(MqttProperties properties, int payloadFormatIndicator) {
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, payloadFormatIndicator));
    }

    public static void addContentTypeToProps(MqttProperties properties, String contentType) {
        properties.add(new MqttProperties.StringProperty(BrokerConstants.CONTENT_TYPE_PROP_ID, contentType));
    }

    public static void addResponseTopicToProps(MqttProperties properties, String responseTopic) {
        properties.add(new MqttProperties.StringProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID, responseTopic));
    }

    public static void addCorrelationDataToProps(MqttProperties properties, byte[] correlationData) {
        properties.add(new MqttProperties.BinaryProperty(BrokerConstants.CORRELATION_DATA_PROP_ID, correlationData));
    }

    public static void addMsgExpiryIntervalToProps(MqttProperties properties, TbQueueMsgHeaders headers) {
        byte[] bytes = headers.get(BrokerConstants.MESSAGE_EXPIRY_INTERVAL);
        if (bytes != null) {
            int messageExpiryInterval = BytesUtil.bytesToInteger(bytes);
            properties.add(
                    new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, messageExpiryInterval)
            );
        }
    }

    public static void addMsgExpiryIntervalToPublish(MqttProperties properties, int messageExpiryInterval) {
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, messageExpiryInterval));
    }

    public static void addAssignedClientIdToProps(MqttProperties properties, String assignedClientId) {
        properties.add(new MqttProperties.StringProperty(
                BrokerConstants.ASSIGNED_CLIENT_IDENTIFIER_PROP_ID,
                assignedClientId)
        );
    }

    public static void addKeepAliveTimeToProps(MqttProperties properties, int keepAliveTimeSeconds) {
        properties.add(new MqttProperties.IntegerProperty(
                BrokerConstants.SERVER_KEEP_ALIVE_PROP_ID,
                keepAliveTimeSeconds)
        );
    }

    public static void addSubsIdentifierAvailableToProps(MqttProperties properties) {
        properties.add(new MqttProperties.IntegerProperty(
                BrokerConstants.SUBSCRIPTION_IDENTIFIER_AVAILABLE_PROP_ID,
                0) // TODO: 14/10/2022 after impl MQTT 5 SubscriptionId feature change this to 1 or remove completely
        );
    }

    public static void addMaxPacketSizeToProps(MqttProperties properties, int maxPacketSize) {
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.MAXIMUM_PACKET_SIZE_PROP_ID, maxPacketSize));
    }

    public static void addSessionExpiryIntervalToProps(MqttProperties properties, int sessionExpiryInterval) {
        properties.add(new MqttProperties.IntegerProperty(
                BrokerConstants.SESSION_EXPIRY_INTERVAL_PROP_ID,
                sessionExpiryInterval)
        );
    }

    public static void addMaxTopicAliasToProps(MqttProperties properties, int maxTopicAlias) {
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.TOPIC_ALIAS_MAX_PROP_ID, maxTopicAlias));
    }

    public static void addResponseInfoToProps(MqttProperties properties, String responseInfo) {
        properties.add(new MqttProperties.StringProperty(BrokerConstants.RESPONSE_INFORMATION_PROP_ID, responseInfo));
    }

    /**
     * Helpful methods
     */

    public static DefaultTbQueueMsgHeaders createHeaders(PublishMsg publishMsg) {
        return createHeaders(publishMsg.getProperties());
    }

    public static DefaultTbQueueMsgHeaders createHeaders(DevicePublishMsg devicePublishMsg) {
        return createHeaders(devicePublishMsg.getProperties());
    }

    public static DefaultTbQueueMsgHeaders createHeaders(MqttProperties properties) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        MqttProperties.IntegerProperty pubExpiryIntervalProperty = getPubExpiryIntervalProperty(properties);
        if (pubExpiryIntervalProperty != null) {
            headers.put(BrokerConstants.MESSAGE_EXPIRY_INTERVAL, BytesUtil.integerToBytes(pubExpiryIntervalProperty.value()));
        }
        return headers;
    }

    public static MsgExpiryResult getMsgExpiryResult(RetainedMsg retainedMsg, long currentTs) {
        return getMsgExpiryResult(retainedMsg.getProperties(), retainedMsg.getCreatedTime(), currentTs);
    }

    public static MsgExpiryResult getMsgExpiryResult(DevicePublishMsg publishMsg, long currentTs) {
        return getMsgExpiryResult(publishMsg.getProperties(), publishMsg.getTime(), currentTs);
    }

    public static MsgExpiryResult getMsgExpiryResult(MqttProperties properties, long createdTime, long currentTs) {
        int messageExpiryInterval;

        MqttProperties.IntegerProperty pubExpiryIntervalProperty = getPubExpiryIntervalProperty(properties);
        if (pubExpiryIntervalProperty == null) {
            return new MsgExpiryResult(false, false, 0);
        } else {
            messageExpiryInterval = pubExpiryIntervalProperty.value();
            if (messageExpiryInterval == 0) {
                return new MsgExpiryResult(true, false, 0);
            }
        }

        long publishMsgExpiryInterval = calculatePublishMsgExpiryInterval(createdTime, messageExpiryInterval, currentTs);
        if (publishMsgExpiryInterval < 0) {
            return new MsgExpiryResult(true, true, 0);
        } else {
            return new MsgExpiryResult(true, false, msToSeconds(publishMsgExpiryInterval));
        }
    }

    public static MsgExpiryResult getMsgExpiryResult(TbQueueMsgHeaders headers, long currentTs) {
        int messageExpiryInterval;
        long createdTime;

        byte[] messageExpiryBytes = headers.get(BrokerConstants.MESSAGE_EXPIRY_INTERVAL);
        if (messageExpiryBytes == null) {
            return new MsgExpiryResult(false, false, 0);
        } else {
            messageExpiryInterval = BytesUtil.bytesToInteger(messageExpiryBytes);
            if (messageExpiryInterval == 0) {
                return new MsgExpiryResult(true, false, 0);
            }
        }

        byte[] createdTimeBytes = headers.get(BrokerConstants.CREATED_TIME);
        if (createdTimeBytes == null) {
            return new MsgExpiryResult(true, false, messageExpiryInterval);
        } else {
            createdTime = BytesUtil.bytesToLong(createdTimeBytes);
        }

        long publishMsgExpiryInterval = calculatePublishMsgExpiryInterval(createdTime, messageExpiryInterval, currentTs);
        if (publishMsgExpiryInterval < 0) {
            return new MsgExpiryResult(true, true, 0);
        } else {
            return new MsgExpiryResult(true, false, msToSeconds(publishMsgExpiryInterval));
        }
    }

    // The PUBLISH packet sent to a Client by the Server MUST contain a Message Expiry Interval set to the received value
    // minus the time that the Application Message has been waiting in the Server
    private static long calculatePublishMsgExpiryInterval(long createdTime, int messageExpiryInterval, long currentTs) {
        return TimeUnit.SECONDS.toMillis(messageExpiryInterval) - (currentTs - createdTime);
    }

    private static int msToSeconds(long value) {
        return Math.toIntExact(TimeUnit.MILLISECONDS.toSeconds(value));
    }

    public static MqttProperties getMqttProperties(PublishMsg publishMsg) {
        MqttProperties properties = new MqttProperties();
        MqttProperties.UserProperties userProperties = getUserProperties(publishMsg);
        if (userProperties != null) {
            properties.add(userProperties);
        }
        MqttProperties.IntegerProperty pubExpiryIntervalProperty = getPubExpiryIntervalProperty(publishMsg);
        if (pubExpiryIntervalProperty != null) {
            properties.add(pubExpiryIntervalProperty);
        }
        MqttProperties.IntegerProperty payloadFormatProperty = getPayloadFormatProperty(publishMsg.getProperties());
        if (payloadFormatProperty != null) {
            properties.add(payloadFormatProperty);
        }
        MqttProperties.StringProperty contentTypeProperty = getContentTypeProperty(publishMsg.getProperties());
        if (contentTypeProperty != null) {
            properties.add(contentTypeProperty);
        }
        MqttProperties.StringProperty responseTopicProperty = getResponseTopicProperty(publishMsg.getProperties());
        if (responseTopicProperty != null) {
            properties.add(responseTopicProperty);
        }
        MqttProperties.BinaryProperty correlationDataProperty = getCorrelationDataProperty(publishMsg.getProperties());
        if (correlationDataProperty != null) {
            properties.add(correlationDataProperty);
        }
        return properties;
    }

}
