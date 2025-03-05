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
package org.thingsboard.mqtt.broker.service.integration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationNotificationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationUplinkQueueProvider;
import org.thingsboard.mqtt.broker.service.IntegrationManagerService;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationUplinkConsumer {

    private final IntegrationUplinkQueueProvider uplinkQueueProvider;
    private final TbmqIntegrationApiService tbmqIntegrationApiService;
    private final IntegrationManagerService integrationManagerService;

    private volatile boolean stopped = false;
    private ExecutorService consumerExecutor;
    private ExecutorService notificationsConsumerExecutor;

    @Value("${queue.integration-uplink.poll-interval}")
    private long pollDuration;
    @Value("${queue.integration-uplink-notifications.poll-interval}")
    private long notificationsPollDuration;

    @PostConstruct
    public void init() {
        consumerExecutor = ThingsBoardExecutors.initSingleExecutorService("ie-uplink-consumer");
        notificationsConsumerExecutor = ThingsBoardExecutors.initSingleExecutorService("ie-uplink-notifications-consumer");

        var consumer = uplinkQueueProvider.getIeUplinkConsumer();
        consumer.subscribe();
        launchConsumer(consumer);

        var notificationsConsumer = uplinkQueueProvider.getIeUplinkNotificationsConsumer();
        notificationsConsumer.subscribe();
        launchNotificationsConsumer(notificationsConsumer);
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (consumerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "IE uplink consumer");
        }
        if (notificationsConsumerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(notificationsConsumerExecutor, "IE uplink notifications consumer");
        }
        integrationManagerService.proceedGracefulShutdown();
    }

    private void launchConsumer(TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> consumer) {
        consumerExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<UplinkIntegrationMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    for (TbProtoQueueMsg<UplinkIntegrationMsgProto> msg : msgs) {
                        try {
                            // TODO: improve the retry strategy
                            tbmqIntegrationApiService.handle(msg, TbCallback.EMPTY);
                        } catch (Throwable e) {
                            log.warn("Failed to process integration msg: {}", msg, e);
                        }
                    }
                    consumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from ie uplink queue.", e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to wait until the server has capacity to handle new ie uplink requests", e2);
                            }
                        }
                    }
                }
            }
            log.info("IE Uplink Consumer stopped");
        });
    }

    private void launchNotificationsConsumer(TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> notificationsConsumer) {
        notificationsConsumerExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> msgs = notificationsConsumer.poll(notificationsPollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    for (TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto> msg : msgs) {
                        try {
                            // TODO: improve the retry strategy
                            handleNotification(msg, TbCallback.EMPTY);
                        } catch (Throwable e) {
                            log.warn("Failed to process integration notification msg: {}", msg, e);
                        }
                    }
                    notificationsConsumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from ie uplink notifications queue.", e);
                        try {
                            Thread.sleep(notificationsPollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to wait until the server has capacity to handle new ie uplink notification requests", e2);
                            }
                        }
                    }
                }
            }
            log.info("IE Uplink Notification Consumer stopped");
        });
    }

    protected void handleNotification(TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto> msg, TbCallback callback) {
        UplinkIntegrationNotificationMsgProto uplinkIntegrationNotificationMsg = msg.getValue();
        if (uplinkIntegrationNotificationMsg.hasIntegrationValidationResponseMsg()) {
            log.trace("Forwarding message to Integration service {}", uplinkIntegrationNotificationMsg.getIntegrationValidationResponseMsg());
            forwardToIntegrationManagerService(uplinkIntegrationNotificationMsg.getIntegrationValidationResponseMsg(), callback);
        } else {
            log.debug("Unexpected message in UplinkIntegrationNotificationMsgProto {}", uplinkIntegrationNotificationMsg);
        }
    }

    private void forwardToIntegrationManagerService(IntegrationValidationResponseProto integrationDownlinkMsg, TbCallback callback) {
        integrationManagerService.handleValidationResponse(integrationDownlinkMsg, callback);
    }
}
