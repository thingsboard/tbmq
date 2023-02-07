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
package org.thingsboard.mqtt.broker.dao.client.application;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;

import java.util.Optional;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationSharedSubscriptionServiceImpl implements ApplicationSharedSubscriptionService {

    private final ApplicationSharedSubscriptionDao applicationSharedSubscriptionDao;

    @Override
    public ApplicationSharedSubscription saveSharedSubscription(ApplicationSharedSubscription subscription) {
        log.trace("Executing saveSharedSubscription [{}]", subscription);
        sharedSubscriptionValidator.validate(subscription);
        try {
            return applicationSharedSubscriptionDao.save(subscription);
        } catch (Exception t) {
            ConstraintViolationException e = DbExceptionUtil.extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null
                    && e.getConstraintName().equalsIgnoreCase("application_shared_subscription_topic_unq_key")) {
                throw new DataValidationException("Specified shared subscription is already registered!");
            } else {
                throw t;
            }
        }
    }

    @Override
    public boolean deleteSharedSubscription(UUID id) {
        log.trace("Executing deleteSharedSubscription [{}]", id);
        return applicationSharedSubscriptionDao.removeById(id);
    }

    @Override
    public ApplicationSharedSubscription findSharedSubscriptionByTopic(String topic) {
        log.trace("Executing findSharedSubscriptionByTopic [{}]", topic);
        return applicationSharedSubscriptionDao.findByTopic(topic);
    }

    @Override
    public ListenableFuture<ApplicationSharedSubscription> findSharedSubscriptionByTopicAsync(String topic) {
        log.trace("Executing findSharedSubscriptionByTopicAsync [{}]", topic);
        return applicationSharedSubscriptionDao.findByTopicAsync(topic);
    }

    @Override
    public PageData<ApplicationSharedSubscription> getSharedSubscriptions(PageLink pageLink) {
        log.trace("Executing getSharedSubscriptions, pageLink [{}]", pageLink);
        validatePageLink(pageLink);
        PageData<ApplicationSharedSubscription> pageData = applicationSharedSubscriptionDao.findAll(pageLink);
        return new PageData<>(pageData.getData(), pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
    }

    @Override
    public Optional<ApplicationSharedSubscription> getSharedSubscriptionById(UUID id) {
        log.trace("Executing getSharedSubscriptionById [{}]", id);
        return Optional.ofNullable(applicationSharedSubscriptionDao.findById(id));
    }

    private final DataValidator<ApplicationSharedSubscription> sharedSubscriptionValidator =
            new DataValidator<>() {
                @Override
                protected void validateCreate(ApplicationSharedSubscription subscription) {
                    if (applicationSharedSubscriptionDao.findByTopic(subscription.getTopic()) != null) {
                        throw new DataValidationException("Such Application Shared Subscription is already created!");
                    }
                }

                @Override
                protected void validateUpdate(ApplicationSharedSubscription subscription) {
                    ApplicationSharedSubscription existingSubscription = applicationSharedSubscriptionDao.findById(subscription.getId());
                    if (existingSubscription == null) {
                        throw new DataValidationException("Unable to update non-existent Application Shared Subscription!");
                    }
                    if (!existingSubscription.getTopic().equals(subscription.getTopic())) {
                        throw new DataValidationException("Updating topic name is not allowed for existed subscription!");
                    }
                    if (existingSubscription.getPartitions() != subscription.getPartitions()) {
                        throw new DataValidationException("Updating partitions count is not allowed for existed subscription!");
                    }
                }

                @Override
                protected void validateDataImpl(ApplicationSharedSubscription subscription) {
                    if (StringUtils.isEmpty(subscription.getName())) {
                        throw new DataValidationException("Application Shared Subscription name should be specified!");
                    }
                    if (StringUtils.isEmpty(subscription.getTopic())) {
                        throw new DataValidationException("Application Shared Subscription topic should be specified!");
                    }
                    if (subscription.getPartitions() <= 0) {
                        throw new DataValidationException("Application Shared Subscription partitions should be specified and greater than 0!");
                    }
                }
            };
}
