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
package org.thingsboard.mqtt.broker.dao.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.dao.AbstractSearchTextDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.WebSocketConnectionEntity;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketConnectionDaoImpl
        extends AbstractSearchTextDao<WebSocketConnectionEntity, WebSocketConnection>
        implements WebSocketConnectionDao {

    private final WebSocketConnectionRepository webSocketConnectionRepository;

    @Override
    protected Class<WebSocketConnectionEntity> getEntityClass() {
        return WebSocketConnectionEntity.class;
    }

    @Override
    protected CrudRepository<WebSocketConnectionEntity, UUID> getCrudRepository() {
        return webSocketConnectionRepository;
    }

    @Override
    public PageData<WebSocketConnection> findAll(UUID userId, PageLink pageLink) {
        return DaoUtil.toPageData(webSocketConnectionRepository.findAll(userId,
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public WebSocketConnection findByUserIdAndName(UUID userId, String name) {
        return DaoUtil.getData(webSocketConnectionRepository.findByUserIdAndName(userId, name));
    }

}
