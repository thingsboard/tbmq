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
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;

import java.util.concurrent.TimeUnit;

public class MqttPropertiesUtil {

    public static boolean isRetainedMsgExpired(RetainedMsg retainedMsg, long currentTs) {
        MqttProperties.IntegerProperty property = getPubExpiryIntervalProperty(retainedMsg);
        if (property != null && property.value() > 0) {
            int messageExpiryInterval = property.value();
            long createdTime = retainedMsg.getCreatedTime();
            return createdTime + TimeUnit.SECONDS.toMillis(messageExpiryInterval) < currentTs;
        }
        return false;
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

    public static MqttProperties.IntegerProperty getPubExpiryIntervalProperty(MqttProperties properties) {
        return (MqttProperties.IntegerProperty) properties.getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
    }

    public static MqttProperties.UserProperties getUserProperties(PublishMsg publishMsg) {
        return getUserProperties(publishMsg.getProperties());
    }

    public static MqttProperties.UserProperties getUserProperties(MqttProperties properties) {
        return (MqttProperties.UserProperties) properties.getProperty(BrokerConstants.USER_PROPERTIES_ID);
    }

}
