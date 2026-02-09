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
package org.thingsboard.mqtt.broker.dao.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.WebSocketSubscriptionEntity;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionDaoImpl
        extends AbstractDao<WebSocketSubscriptionEntity, WebSocketSubscription>
        implements WebSocketSubscriptionDao {

    private final WebSocketSubscriptionRepository webSocketSubscriptionRepository;

    @Override
    protected Class<WebSocketSubscriptionEntity> getEntityClass() {
        return WebSocketSubscriptionEntity.class;
    }

    @Override
    protected CrudRepository<WebSocketSubscriptionEntity, UUID> getCrudRepository() {
        return webSocketSubscriptionRepository;
    }

    @Override
    public PageData<WebSocketSubscription> findAllByWebSocketConnectionId(UUID webSocketConnectionId, PageLink pageLink) {
        return DaoUtil.toPageData(webSocketSubscriptionRepository.findAllByWebSocketConnectionId(webSocketConnectionId,
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink)));
    }

}
