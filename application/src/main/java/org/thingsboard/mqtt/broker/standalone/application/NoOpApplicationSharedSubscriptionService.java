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
package org.thingsboard.mqtt.broker.standalone.application;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;

import java.util.Optional;
import java.util.UUID;

@Service
@StandaloneComponent
public class NoOpApplicationSharedSubscriptionService implements ApplicationSharedSubscriptionService {

    private static final String NOT_SUPPORTED = "Application shared subscriptions are not supported in TBMQ Lite";

    @Override
    public ApplicationSharedSubscription saveSharedSubscription(ApplicationSharedSubscription subscription) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public boolean deleteSharedSubscription(UUID id) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public ApplicationSharedSubscription findSharedSubscriptionByTopic(String topic) {
        return null;
    }

    @Override
    public ListenableFuture<ApplicationSharedSubscription> findSharedSubscriptionByTopicAsync(String topic) {
        return Futures.immediateFuture(null);
    }

    @Override
    public PageData<ApplicationSharedSubscription> getSharedSubscriptions(PageLink pageLink) {
        return PageData.emptyPageData();
    }

    @Override
    public Optional<ApplicationSharedSubscription> getSharedSubscriptionById(UUID id) {
        return Optional.empty();
    }

}
