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
package org.thingsboard.mqtt.broker.dao.client.application;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.ApplicationSharedSubscriptionEntity;

import java.util.Objects;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.model.ApplicationSharedSubscriptionEntity.appSharedSubscriptionColumnMap;

@Component
@RequiredArgsConstructor
public class ApplicationSharedSubscriptionDaoImpl
        extends AbstractDao<ApplicationSharedSubscriptionEntity, ApplicationSharedSubscription>
        implements ApplicationSharedSubscriptionDao {

    private final ApplicationSharedSubscriptionRepository applicationSharedSubscriptionRepository;

    @Override
    protected Class<ApplicationSharedSubscriptionEntity> getEntityClass() {
        return ApplicationSharedSubscriptionEntity.class;
    }

    @Override
    protected CrudRepository<ApplicationSharedSubscriptionEntity, UUID> getCrudRepository() {
        return applicationSharedSubscriptionRepository;
    }

    @Override
    public ApplicationSharedSubscription findByTopic(String topic) {
        return DaoUtil.getData(applicationSharedSubscriptionRepository.findByTopic(topic));
    }

    @Override
    public ListenableFuture<ApplicationSharedSubscription> findByTopicAsync(String topic) {
        return service.submit(() -> findByTopic(topic));
    }

    @Override
    public PageData<ApplicationSharedSubscription> findAll(PageLink pageLink) {
        return DaoUtil.toPageData(applicationSharedSubscriptionRepository.findAll(
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink, appSharedSubscriptionColumnMap)));
    }
}
