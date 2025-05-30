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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.producer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.BlockedClientQueueFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedClientProducerServiceImpl implements BlockedClientProducerService {

    private final BlockedClientQueueFactory blockedClientQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    @Value("${queue.blocked-client.acknowledge-wait-timeout-ms:500}")
    private long ackTimeoutMs;

    private TbQueueProducer<TbProtoQueueMsg<BlockedClientProto>> blockedClientProducer;

    @PostConstruct
    public void init() {
        blockedClientProducer = blockedClientQueueFactory.createProducer();
    }

    @PreDestroy
    public void destroy() {
        if (blockedClientProducer != null) {
            blockedClientProducer.stop();
        }
    }

    @Override
    public void persistBlockedClient(String key, BlockedClientProto blockedClientProto, BasicCallback callback) {
        log.trace("[{}] Persisting blocked client - {}", key, blockedClientProto);
        blockedClientProducer.send(generateRequest(key, blockedClientProto), new BlockedClientCallback(callback));
    }

    @Override
    public void persistDummyBlockedClient(String key, BlockedClientProto blockedClientProto) throws QueuePersistenceException {
        log.trace("[{}] Persisting dummy blocked client - {}", key, blockedClientProto);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch updateWaiter = new CountDownLatch(1);

        blockedClientProducer.send(generateRequest(key, blockedClientProto), new BlockedClientDummyCallback(errorRef, updateWaiter));

        boolean waitSuccessful = false;
        try {
            waitSuccessful = updateWaiter.await(ackTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errorRef.getAndSet(e);
        }
        Throwable error = errorRef.get();
        if (!waitSuccessful || error != null) {
            log.warn("[{}] Failed to persist blocked client. Reason - {}.",
                    key, error != null ? error.getMessage() : "timeout waiting");
            if (error != null) {
                log.trace("Detailed error:", error);
            }
            if (!waitSuccessful) {
                throw new QueuePersistenceException("Timed out to persist blocked client " + key);
            } else {
                throw new QueuePersistenceException("Failed to persist blocked client " + key);
            }
        }
    }

    private TbProtoQueueMsg<BlockedClientProto> generateRequest(String key, BlockedClientProto blockedClientProto) {
        TbProtoQueueMsg<BlockedClientProto> request = new TbProtoQueueMsg<>(key, blockedClientProto);
        request.getHeaders().put(BrokerConstants.SERVICE_ID_HEADER, BytesUtil.stringToBytes(serviceInfoProvider.getServiceId()));
        return request;
    }

    @RequiredArgsConstructor
    private static class BlockedClientCallback implements TbQueueCallback {

        private final BasicCallback callback;

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            log.trace("Blocked client sent successfully: {}", metadata);
            if (callback != null) {
                callback.onSuccess();
            }
        }

        @Override
        public void onFailure(Throwable t) {
            log.warn("Failed to persist blocked client", t);
            if (callback != null) {
                callback.onFailure(t);
            }
        }
    }

    @RequiredArgsConstructor
    private static class BlockedClientDummyCallback implements TbQueueCallback {

        private final AtomicReference<Throwable> errorRef;
        private final CountDownLatch updateWaiter;

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            log.trace("Blocked dummy client sent successfully: {}", metadata);
            updateWaiter.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            log.warn("Failed to persist blocked dummy client", t);
            errorRef.getAndSet(t);
            updateWaiter.countDown();
        }
    }
}
