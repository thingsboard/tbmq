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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.RetainedMsgQueueFactory;
import org.thingsboard.mqtt.broker.util.BytesUtil;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class RetainedMsgPersistenceServiceImpl implements RetainedMsgPersistenceService {

    @Value("${queue.retained-msg.acknowledge-wait-timeout-ms}")
    private long ackTimeoutMs;

    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.RetainedMsgProto>> retainedMsgProducer;
    private final ServiceInfoProvider serviceInfoProvider;

    public RetainedMsgPersistenceServiceImpl(RetainedMsgQueueFactory retainedMsgQueueFactory, ServiceInfoProvider serviceInfoProvider) {
        this.retainedMsgProducer = retainedMsgQueueFactory.createProducer();
        this.serviceInfoProvider = serviceInfoProvider;
    }

    @Override
    public void persistRetainedMsgAsync(String topic, QueueProtos.RetainedMsgProto retainedMsgProto, BasicCallback callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting retained msg asynchronously - {}", topic, retainedMsgProto);
        }
        retainedMsgProducer.send(generateRequest(topic, retainedMsgProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (callback != null) {
                    callback.onFailure(t);
                }
            }
        });
    }

    @Override
    public void persistRetainedMsgAsync(String topic, QueueProtos.RetainedMsgProto retainedMsgProto, BasicCallback callback, int messageExpiryInterval) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting retained msg asynchronously - {} with expiry interval {}", topic, retainedMsgProto, messageExpiryInterval);
        }
        retainedMsgProducer.send(generateRequest(topic, retainedMsgProto, messageExpiryInterval), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (callback != null) {
                    callback.onFailure(t);
                }
            }
        });
    }

    @Override
    public void persistRetainedMsgSync(String topic, QueueProtos.RetainedMsgProto retainedMsgProto) throws QueuePersistenceException {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting retained msg synchronously - {}", topic, retainedMsgProto);
        }
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch updateWaiter = new CountDownLatch(1);
        retainedMsgProducer.send(generateRequest(topic, retainedMsgProto), new TbQueueCallback() {
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
            waitSuccessful = updateWaiter.await(ackTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errorRef.getAndSet(e);
        }
        Throwable error = errorRef.get();
        if (!waitSuccessful || error != null) {
            log.warn("[{}] Failed to update retained msg. Reason - {}.",
                    topic, error != null ? error.getMessage() : "timeout waiting");
            if (error != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Detailed error:", error);
                }
            }
            if (!waitSuccessful) {
                throw new QueuePersistenceException("Timed out to update retained msg for topic " + topic);
            } else {
                throw new QueuePersistenceException("Failed to update retained msg for topic " + topic);
            }
        }
    }

    private TbProtoQueueMsg<QueueProtos.RetainedMsgProto> generateRequest(String topic, QueueProtos.RetainedMsgProto retainedMsgProto) {
        TbProtoQueueMsg<QueueProtos.RetainedMsgProto> request = new TbProtoQueueMsg<>(topic, retainedMsgProto);
        request.getHeaders().put(BrokerConstants.SERVICE_ID_HEADER, BytesUtil.stringToBytes(serviceInfoProvider.getServiceId()));
        return request;
    }

    private TbProtoQueueMsg<QueueProtos.RetainedMsgProto> generateRequest(String topic, QueueProtos.RetainedMsgProto retainedMsgProto, int messageExpiryInterval) {
        TbProtoQueueMsg<QueueProtos.RetainedMsgProto> request = generateRequest(topic, retainedMsgProto);
        request.getHeaders().put(BrokerConstants.MESSAGE_EXPIRY_INTERVAL, BytesUtil.integerToBytes(messageExpiryInterval));
        return request;
    }

    @PreDestroy
    public void destroy() {
        retainedMsgProducer.stop();
    }
}
