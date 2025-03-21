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
package org.thingsboard.mqtt.broker.service.mqtt.client.disconnect;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.gen.queue.DisconnectClientCommandProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DisconnectClientCommandQueueFactory;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisconnectClientCommandServiceImpl implements DisconnectClientCommandService {

    private final DisconnectClientCommandQueueFactory disconnectClientCommandQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final DisconnectClientCommandHelper helper;

    private TbQueueProducer<TbProtoQueueMsg<DisconnectClientCommandProto>> clientDisconnectCommandProducer;

    @PostConstruct
    public void init() {
        this.clientDisconnectCommandProducer = disconnectClientCommandQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    @Override
    public void disconnectOnSessionConflict(String serviceId, String clientId, UUID sessionId, boolean newSessionCleanStart) {
        DisconnectClientCommandProto disconnectCommand = getDisconnectCommand(sessionId, newSessionCleanStart, DisconnectReasonType.ON_CONFLICTING_SESSIONS);
        send(serviceId, clientId, sessionId, disconnectCommand);
    }

    @Override
    public void disconnectSessionOnAdminAction(String serviceId, String clientId, UUID sessionId, boolean newSessionCleanStart) {
        DisconnectClientCommandProto disconnectCommand = getDisconnectCommand(sessionId, newSessionCleanStart, DisconnectReasonType.ON_ADMINISTRATIVE_ACTION);
        send(serviceId, clientId, sessionId, disconnectCommand);
    }

    private void send(String serviceId, String clientId, UUID sessionId, DisconnectClientCommandProto disconnectCommand) {
        String topic = helper.getServiceTopic(serviceId);
        clientDisconnectCommandProducer.send(topic, null, new TbProtoQueueMsg<>(clientId, disconnectCommand), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                if (log.isTraceEnabled()) {
                    log.trace("[{}] Disconnect command for session {} sent successfully.", clientId, sessionId);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to send command for session {}.", clientId, sessionId, t);
            }
        });
    }

    private DisconnectClientCommandProto getDisconnectCommand(UUID sessionId, boolean newSessionCleanStart, DisconnectReasonType reasonType) {
        return ProtoConverter.createDisconnectClientCommandProto(sessionId, newSessionCleanStart, reasonType.name());
    }

    @PreDestroy
    public void destroy() {
        if (clientDisconnectCommandProducer != null) {
            clientDisconnectCommandProducer.stop();
        }
    }

}
