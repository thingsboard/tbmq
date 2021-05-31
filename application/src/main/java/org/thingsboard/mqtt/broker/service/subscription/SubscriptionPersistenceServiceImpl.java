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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSubscriptionsQueueFactory;
import org.thingsboard.mqtt.broker.util.BytesUtil;

import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SubscriptionPersistenceServiceImpl implements SubscriptionPersistenceService {
    @Value("${queue.client-subscriptions.acknowledge-wait-timeout-ms}")
    private long ackTimeoutMs;

    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> clientSubscriptionsProducer;
    private final ServiceInfoProvider serviceInfoProvider;

    public SubscriptionPersistenceServiceImpl(ClientSubscriptionsQueueFactory clientSubscriptionsQueueFactory, ServiceInfoProvider serviceInfoProvider) {
        this.clientSubscriptionsProducer = clientSubscriptionsQueueFactory.createProducer();
        this.serviceInfoProvider = serviceInfoProvider;
    }

    @Override
    public void persistClientSubscriptions(String clientId, Set<TopicSubscription> clientSubscriptions) {
        // TODO: make this async again
        QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto = ProtoConverter.convertToClientSubscriptionsProto(clientSubscriptions);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch updateWaiter = new CountDownLatch(1);
        clientSubscriptionsProducer.send(generateRequest(clientId, clientSubscriptionsProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        updateWaiter.countDown();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        errorRef.getAndSet(t);
                        updateWaiter.countDown();
                    }
                });

        boolean waitSuccessful = false;
        try {
            // TODO is this OK that the thread is blocked?
            waitSuccessful = updateWaiter.await(ackTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errorRef.getAndSet(e);
        }
        Throwable error = errorRef.get();
        if (!waitSuccessful || error != null) {
            log.warn("[{}] Failed to update client subscriptions. Reason - {}.",
                    clientId, error != null ? error.getMessage() : "timeout waiting");
            if (error != null) {
                log.trace("Detailed error:", error);
            }
            throw new MqttException("Failed to update client subscriptions.");
        }
    }

    private TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto> generateRequest(String clientId, QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto) {
        TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto> request = new TbProtoQueueMsg<>(clientId, clientSubscriptionsProto);
        request.getHeaders().put(SubscriptionConst.SERVICE_ID_HEADER, BytesUtil.stringToBytes(serviceInfoProvider.getServiceId()));
        return request;
    }

    @PreDestroy
    public void destroy() {
        clientSubscriptionsProducer.stop();
    }
}
