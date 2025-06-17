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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/shared/subs")
@Slf4j
public class AppSharedSubscriptionController extends BaseController {

    private final ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    private final ApplicationTopicService applicationTopicService;
    private final TopicValidationService topicValidationService;

    @Value("${queue.kafka.enable-topic-deletion:true}")
    private boolean enableTopicDeletion;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public ApplicationSharedSubscription saveSharedSubscription(@RequestBody ApplicationSharedSubscription sharedSubscription) throws ThingsboardException {
        topicValidationService.validateTopicFilter(sharedSubscription.getTopicFilter());

        ApplicationSharedSubscription applicationSharedSubscription =
                checkNotNull(applicationSharedSubscriptionService.saveSharedSubscription(sharedSubscription));
        if (sharedSubscription.getId() == null) {
            createKafkaTopic(applicationSharedSubscription);
        }
        return applicationSharedSubscription;
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

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"pageSize", "page"})
    public PageData<ApplicationSharedSubscription> getSharedSubscriptions(@RequestParam int pageSize,
                                                                          @RequestParam int page,
                                                                          @RequestParam(required = false) String textSearch,
                                                                          @RequestParam(required = false) String sortProperty,
                                                                          @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(applicationSharedSubscriptionService.getSharedSubscriptions(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/{id}")
    public ApplicationSharedSubscription getSharedSubscriptionById(@PathVariable String id) throws ThingsboardException {
        checkParameter("id", id);
        return checkNotNull(applicationSharedSubscriptionService.getSharedSubscriptionById(toUUID(id)).orElse(null));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"topic"})
    public ApplicationSharedSubscription getSharedSubscriptionByTopic(@RequestParam String topic) throws ThingsboardException {
        checkParameter("topic", topic);
        return checkNotNull(applicationSharedSubscriptionService.findSharedSubscriptionByTopic(topic));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public void deleteSharedSubscription(@PathVariable String id) throws ThingsboardException {
        ApplicationSharedSubscription sharedSubscription = getSharedSubscriptionById(id);
        boolean removed = applicationSharedSubscriptionService.deleteSharedSubscription(sharedSubscription.getId());
        if (removed && enableTopicDeletion) {
            applicationTopicService.deleteSharedTopic(sharedSubscription);
        }
    }
}
