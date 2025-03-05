/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

public class MqttQosUtil {

    public static int downgradeQos(Subscription subscription, PublishMsgProto publishMsgProto) {
        return downgradeQos(subscription.getQos(), publishMsgProto.getQos());
    }

    public static int downgradeQos(TopicSharedSubscription subscription, int qos) {
        return downgradeQos(subscription.getQos(), qos);
    }

    public static int downgradeQos(TopicSubscription subscription, RetainedMsg retainedMsg) {
        return downgradeQos(subscription.getQos(), retainedMsg.getQos());
    }

    public static int downgradeQos(TopicSharedSubscription subscription, DevicePublishMsg msg) {
        return downgradeQos(subscription.getQos(), msg.getQos());
    }

    private static int downgradeQos(int subscribeQos, int publishQos) {
        return Math.min(subscribeQos, publishQos);
    }

}
