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
package org.thingsboard.mqtt.broker.service.entity.appsharedsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbAppSharedSubscriptionService extends AbstractTbEntityService implements TbAppSharedSubscriptionService {

    private final ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    private final ApplicationTopicService applicationTopicService;

    @Value("${queue.kafka.enable-topic-deletion:true}")
    private boolean enableTopicDeletion;

    @Override
    public ApplicationSharedSubscription save(ApplicationSharedSubscription sharedSubscription, User currentUser) throws ThingsboardException {
        try {
            ApplicationSharedSubscription saved = checkNotNull(applicationSharedSubscriptionService.saveSharedSubscription(sharedSubscription));
            if (sharedSubscription.getId() == null) {
                createKafkaTopic(saved);
            }
            return saved;
        } catch (Exception e) {
            if (e instanceof ThingsboardException te) {
                throw te;
            }
            throw new ThingsboardException(e, ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void delete(ApplicationSharedSubscription sharedSubscription, User currentUser) {
        boolean removed = applicationSharedSubscriptionService.deleteSharedSubscription(sharedSubscription.getId());
        if (removed && enableTopicDeletion) {
            applicationTopicService.deleteSharedTopic(sharedSubscription);
        }
    }

    @Override
    public void createKafkaTopics(User currentUser) {
        PageLink pageLink = new PageLink(BrokerConstants.DEFAULT_PAGE_SIZE);
        PageData<ApplicationSharedSubscription> pageData;
        do {
            pageData = applicationSharedSubscriptionService.getSharedSubscriptions(pageLink);
            pageData.getData().forEach(sharedSubscription -> {
                try {
                    applicationTopicService.createSharedTopic(sharedSubscription);
                } catch (Exception e) {
                    log.error("Failed to create kafka topic for shared subscription {}", sharedSubscription, e);
                }
            });
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
    }

    @Override
    public void deleteKafkaTopics(User currentUser) {
        if (!enableTopicDeletion) {
            log.debug("Cannot delete topics due to TB_KAFKA_ENABLE_TOPIC_DELETION is set to false");
            return;
        }
        PageLink pageLink = new PageLink(BrokerConstants.DEFAULT_PAGE_SIZE);
        PageData<ApplicationSharedSubscription> pageData;
        do {
            pageData = applicationSharedSubscriptionService.getSharedSubscriptions(pageLink);
            pageData.getData().forEach(sharedSubscription -> {
                try {
                    applicationTopicService.deleteSharedTopic(sharedSubscription);
                } catch (Exception e) {
                    log.error("Failed to delete kafka topic for shared subscription {}", sharedSubscription, e);
                }
            });
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
    }

    private void createKafkaTopic(ApplicationSharedSubscription subscription) throws ThingsboardException {
        try {
            applicationTopicService.createSharedTopic(subscription);
        } catch (Exception e) {
            log.error("Failed to create shared Kafka topic", e);
            applicationSharedSubscriptionService.deleteSharedSubscription(subscription.getId());
            throw new ThingsboardException(e, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

}
