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
package org.thingsboard.mqtt.broker.actors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.ClientActorConfiguration;
import org.thingsboard.mqtt.broker.actors.client.service.channel.ChannelBackpressureManager;
import org.thingsboard.mqtt.broker.actors.device.DeviceActorConfiguration;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ActorProcessingMetricService;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class ActorSystemContext {

    private final TbActorSystem actorSystem;
    private final DeviceMsgService deviceMsgService;
    private final MqttMsgDeliveryService mqttMsgDeliveryService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ActorProcessingMetricService actorProcessingMetricService;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final ChannelBackpressureManager channelBackpressureManager;

    private final ClientActorContext clientActorContext;

    private final DeviceActorConfiguration deviceActorConfiguration;
    private final ClientActorConfiguration clientActorConfiguration;

    public void scheduleMsgWithDelay(TbActorCtx ctx, TbActorMsg msg, long delayInMs) {
        log.debug("Scheduling msg {} with delay {} ms", msg, delayInMs);
        if (delayInMs > 0) {
            actorSystem.getScheduler().schedule(() -> ctx.tell(msg), delayInMs, TimeUnit.MILLISECONDS);
        } else {
            ctx.tell(msg);
        }
    }

    public void scheduleHighPriorityMsgWithDelay(TbActorCtx ctx, TbActorMsg msg, long delayInSec) {
        log.debug("Scheduling high priority msg {} with delay {}s", msg, delayInSec);
        if (delayInSec > 0) {
            actorSystem.getScheduler().schedule(() -> ctx.tellWithHighPriority(msg), delayInSec, TimeUnit.SECONDS);
        } else {
            ctx.tellWithHighPriority(msg);
        }
    }

}
