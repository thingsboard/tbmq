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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;

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

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public ApplicationSharedSubscription saveSharedSubscription(@RequestBody ApplicationSharedSubscription sharedSubscription) throws ThingsboardException {
        checkNotNull(sharedSubscription);
        try {
            topicValidationService.validateTopicFilter(sharedSubscription.getTopic());

            ApplicationSharedSubscription applicationSharedSubscription =
                    checkNotNull(applicationSharedSubscriptionService.saveSharedSubscription(sharedSubscription));
            if (sharedSubscription.getId() == null) {
                createKafkaTopic(applicationSharedSubscription);
            }
            return applicationSharedSubscription;
        } catch (Exception e) {
            throw handleException(e);
        }
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

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ApplicationSharedSubscription> getSharedSubscriptions(@RequestParam int pageSize,
                                                                          @RequestParam int page,
                                                                          @RequestParam(required = false) String textSearch,
                                                                          @RequestParam(required = false) String sortProperty,
                                                                          @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(applicationSharedSubscriptionService.getSharedSubscriptions(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public ApplicationSharedSubscription getSharedSubscriptionById(@PathVariable String id) throws ThingsboardException {
        try {
            return applicationSharedSubscriptionService.getSharedSubscriptionById(toUUID(id)).orElse(null);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"topic"}, method = RequestMethod.GET)
    public ApplicationSharedSubscription getSharedSubscriptionByTopic(@RequestParam String topic) throws ThingsboardException {
        try {
            return checkNotNull(applicationSharedSubscriptionService.findSharedSubscriptionByTopic(topic));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteSharedSubscription(@PathVariable String id) throws ThingsboardException {
        try {
            ApplicationSharedSubscription sharedSubscription = getSharedSubscriptionById(id);
            if (sharedSubscription == null) {
                return;
            }
            boolean removed = applicationSharedSubscriptionService.deleteSharedSubscription(sharedSubscription.getId());
            if (removed && enableTopicDeletion) {
                applicationTopicService.deleteSharedTopic(sharedSubscription);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
