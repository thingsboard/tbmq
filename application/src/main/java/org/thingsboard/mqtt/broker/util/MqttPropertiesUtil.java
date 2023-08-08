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

    public static MqttProperties.IntegerProperty getTopicAliasProperty(MqttProperties properties) {
        return getIntegerProperty(properties, BrokerConstants.TOPIC_ALIAS_PROP_ID);
    }

    private static MqttProperties.IntegerProperty getIntegerProperty(MqttProperties properties, int propertyId) {
        MqttProperties.MqttProperty property = properties.getProperty(propertyId);
        return property != null ? (MqttProperties.IntegerProperty) property : null;
    }

    public static MqttProperties.UserProperties getUserProperties(PublishMsg publishMsg) {
        return getUserProperties(publishMsg.getProperties());
    }

    public static MqttProperties.UserProperties getUserProperties(MqttProperties properties) {
        return (MqttProperties.UserProperties) properties.getProperty(BrokerConstants.USER_PROPERTIES_ID);
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

    public static DefaultTbQueueMsgHeaders createHeaders(PublishMsg publishMsg) {
        return createHeaders(publishMsg.getProperties());
    }

    public static DefaultTbQueueMsgHeaders createHeaders(DevicePublishMsg devicePublishMsg) {
        return createHeaders(devicePublishMsg.getProperties());
    }

    public static DefaultTbQueueMsgHeaders createHeaders(MqttProperties properties) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        MqttProperties.IntegerProperty pubExpiryIntervalProperty = MqttPropertiesUtil.getPubExpiryIntervalProperty(properties);
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
}
