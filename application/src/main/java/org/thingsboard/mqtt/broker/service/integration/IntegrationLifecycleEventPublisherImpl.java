/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.TopicSubscriptionProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.integration.IntegrationEventMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.stats.DroppedLifecycleEventStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationLifecycleEventPublisherImpl implements IntegrationLifecycleEventPublisher {

    private final IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    private final IntegrationEventMsgQueuePublisher integrationEventMsgQueuePublisher;
    private final StatsManager statsManager;

    private DroppedLifecycleEventStats droppedLifecycleEventStats;

    @PostConstruct
    public void init() {
        this.droppedLifecycleEventStats = statsManager.getDroppedLifecycleEventStats();
    }

    @Override
    public void publishConnected(SessionInfo sessionInfo) {
        try {
            Set<String> integrationIds = lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED);
            if (integrationIds.isEmpty()) {
                return;
            }
            ClientLifecycleEventMsgProto proto = ClientLifecycleEventMsgProto.newBuilder()
                    .setEventType(ClientLifecycleEventType.CLIENT_CONNECTED.name())
                    .setClientId(sessionInfo.getClientInfo().getClientId())
                    .setSessionId(sessionInfo.getSessionId().toString())
                    .setIpAddress(toIpString(sessionInfo.getClientInfo().getClientIpAdr()))
                    .setTs(System.currentTimeMillis())
                    .setTbmqNode(sessionInfo.getServiceId())
                    .setCleanStart(sessionInfo.isCleanStart())
                    .setKeepAlive(sessionInfo.getKeepAlive())
                    .build();
            publish(integrationIds, proto);
        } catch (Throwable t) {
            onPublishError(ClientLifecycleEventType.CLIENT_CONNECTED, t);
        }
    }

    @Override
    public void publishDisconnected(SessionInfo sessionInfo, DisconnectReasonType reasonType) {
        try {
            Set<String> integrationIds = lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_DISCONNECTED);
            if (integrationIds.isEmpty()) {
                return;
            }
            ClientLifecycleEventMsgProto proto = ClientLifecycleEventMsgProto.newBuilder()
                    .setEventType(ClientLifecycleEventType.CLIENT_DISCONNECTED.name())
                    .setClientId(sessionInfo.getClientInfo().getClientId())
                    .setSessionId(sessionInfo.getSessionId().toString())
                    .setIpAddress(toIpString(sessionInfo.getClientInfo().getClientIpAdr()))
                    .setTs(System.currentTimeMillis())
                    .setTbmqNode(sessionInfo.getServiceId())
                    .setDisconnectReason(reasonType.name())
                    .setClientInitiated(isClientInitiated(reasonType))
                    .build();
            publish(integrationIds, proto);
        } catch (Throwable t) {
            onPublishError(ClientLifecycleEventType.CLIENT_DISCONNECTED, t);
        }
    }

    @Override
    public void publishSubscribed(SessionInfo sessionInfo, List<TopicSubscription> subscriptions) {
        try {
            Set<String> integrationIds = lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED);
            if (integrationIds.isEmpty()) {
                return;
            }
            ClientLifecycleEventMsgProto.Builder builder = ClientLifecycleEventMsgProto.newBuilder()
                    .setEventType(ClientLifecycleEventType.CLIENT_SUBSCRIBED.name())
                    .setClientId(sessionInfo.getClientInfo().getClientId())
                    .setSessionId(sessionInfo.getSessionId().toString())
                    .setIpAddress(toIpString(sessionInfo.getClientInfo().getClientIpAdr()))
                    .setTs(System.currentTimeMillis())
                    .setTbmqNode(sessionInfo.getServiceId());
            for (TopicSubscription sub : subscriptions) {
                builder.addSubscriptions(TopicSubscriptionProto.newBuilder()
                        .setTopic(sub.getTopicFilter())
                        .setQos(sub.getQos())
                        .build());
            }
            publish(integrationIds, builder.build());
        } catch (Throwable t) {
            onPublishError(ClientLifecycleEventType.CLIENT_SUBSCRIBED, t);
        }
    }

    @Override
    public void publishUnsubscribed(SessionInfo sessionInfo, List<String> topicFilters) {
        try {
            Set<String> integrationIds = lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED);
            if (integrationIds.isEmpty()) {
                return;
            }
            ClientLifecycleEventMsgProto proto = ClientLifecycleEventMsgProto.newBuilder()
                    .setEventType(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED.name())
                    .setClientId(sessionInfo.getClientInfo().getClientId())
                    .setSessionId(sessionInfo.getSessionId().toString())
                    .setIpAddress(toIpString(sessionInfo.getClientInfo().getClientIpAdr()))
                    .setTs(System.currentTimeMillis())
                    .setTbmqNode(sessionInfo.getServiceId())
                    .addAllTopicFilters(topicFilters)
                    .build();
            publish(integrationIds, proto);
        } catch (Throwable t) {
            onPublishError(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED, t);
        }
    }

    private void publish(Set<String> integrationIds, ClientLifecycleEventMsgProto lifecycleMsg) {
        for (String integrationId : integrationIds) {
            try {
                TbProtoQueueMsg<ClientLifecycleEventMsgProto> queueMsg = new TbProtoQueueMsg<>(UUID.randomUUID(), lifecycleMsg);
                integrationEventMsgQueuePublisher.sendEventMsg(integrationId, queueMsg, PublishMsgCallback.EMPTY);
            } catch (Throwable t) {
                log.warn("[{}] Failed to publish lifecycle event [{}]; dropping.", integrationId, lifecycleMsg.getEventType(), t);
                droppedLifecycleEventStats.increment();
            }
        }
    }

    private void onPublishError(ClientLifecycleEventType eventType, Throwable t) {
        log.warn("Failed to publish lifecycle event [{}]; dropping.", eventType, t);
        droppedLifecycleEventStats.increment();
    }

    private String toIpString(byte[] ipAdr) {
        if (ipAdr == null || ipAdr.length == 0) {
            return "";
        }
        try {
            return InetAddress.getByAddress(ipAdr).getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isClientInitiated(DisconnectReasonType reasonType) {
        return reasonType == DisconnectReasonType.ON_DISCONNECT_MSG
                || reasonType == DisconnectReasonType.ON_DISCONNECT_AND_WILL_MSG;
    }

}
