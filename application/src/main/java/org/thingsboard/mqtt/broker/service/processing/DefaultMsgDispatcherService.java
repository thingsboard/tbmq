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
package org.thingsboard.mqtt.broker.service.processing;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.SessionInfoProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionService;

import javax.annotation.PreDestroy;

@Service
@Slf4j
public class DefaultMsgDispatcherService implements MsgDispatcherService {

    private final SubscriptionService subscriptionService;
    private final TbQueueProducer<TbProtoQueueMsg<PublishMsgProto>> publishMsgProducer;

    public DefaultMsgDispatcherService(SubscriptionService subscriptionService,
                                       PublishMsgQueueFactory publishMsgQueueFactory) {
        this.subscriptionService = subscriptionService;
        this.publishMsgProducer = publishMsgQueueFactory.createProducer();
    }

    @PreDestroy
    public void destroy() {
        publishMsgProducer.stop();
    }

    @Override
    public void acknowledgePublishMsg(SessionInfoProto sessionInfoProto, MqttPublishMessage publishMessage, TbQueueCallback callback) {
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfoProto, publishMessage);
        String topicName = publishMessage.variableHeader().topicName();
        TopicInfo topicInfo = new TopicInfo(publishMsgProducer.getDefaultTopic());
        publishMsgProducer.send(topicInfo, new TbProtoQueueMsg<>(topicName, publishMsgProto), callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto) {
        // todo: persist if required

        for (Subscription subscription : subscriptionService.getSubscriptions(publishMsgProto.getTopicName())) {
            subscription.getListener().onPublishMsg(subscription.getMqttQoS(), publishMsgProto);
        }
    }

}
