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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@ClusterComponent
@RequiredArgsConstructor
public class QueueClientSessionEventResponseSender implements ClientSessionEventResponseSender {

    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<ClientSessionEventResponseProto>> eventResponseProducer;
    private ExecutorService eventResponseSenderExecutor;

    @Value("${queue.client-session-event-response.response-sender-threads}")
    private int responseSenderThreads;

    @PostConstruct
    public void init() {
        eventResponseProducer = clientSessionEventQueueFactory.createEventResponseProducer(serviceInfoProvider.getServiceId());
        eventResponseSenderExecutor = Executors.newFixedThreadPool(responseSenderThreads);
    }

    @Override
    public void send(String clientId, ConnectionRequestInfo requestInfo, ClientSessionEventResponseProto response) {
        TbQueueMsgHeaders headers = createResponseHeaders(requestInfo.getRequestId());

        eventResponseSenderExecutor.execute(() ->
                eventResponseProducer.send(
                        requestInfo.getResponseTopic(),
                        null,
                        new TbProtoQueueMsg<>(clientId, response, headers),
                        new TbQueueCallback() {
                            @Override
                            public void onSuccess(TbQueueMsgMetadata metadata) {
                                log.trace("[{}][{}] Successfully sent response.", clientId, requestInfo.getRequestId());
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.warn("[{}][{}] Failed to send response.", clientId, requestInfo.getRequestId(), t);
                            }
                        }));
    }

    private DefaultTbQueueMsgHeaders createResponseHeaders(UUID requestId) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        headers.put(BrokerConstants.REQUEST_ID_HEADER, BytesUtil.uuidToBytes(requestId));
        return headers;
    }

    @PreDestroy
    public void destroy() {
        if (eventResponseProducer != null) {
            eventResponseProducer.stop();
        }
        if (eventResponseSenderExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(eventResponseSenderExecutor, "Session cluster manager");
        }
    }

}
