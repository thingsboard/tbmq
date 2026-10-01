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
package org.thingsboard.mqtt.broker.dao.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.AuthorizationPolicy;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.AuthorizationPolicyEntity;

import java.util.Objects;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.model.AuthorizationPolicyEntity.authorizationPolicyColumnMap;

@Component
@RequiredArgsConstructor
public class AuthorizationPolicyDaoImpl extends AbstractDao<AuthorizationPolicyEntity, AuthorizationPolicy>
        implements AuthorizationPolicyDao {

    private final AuthorizationPolicyRepository repository;

    @Override
    protected Class<AuthorizationPolicyEntity> getEntityClass() {
        return AuthorizationPolicyEntity.class;
    }

    @Override
    protected CrudRepository<AuthorizationPolicyEntity, UUID> getCrudRepository() {
        return repository;
    }

    @Override
    public AuthorizationPolicy findByName(String name) {
        return DaoUtil.getData(repository.findByName(name));
    }

    @Override
    public PageData<AuthorizationPolicy> findAll(PageLink pageLink) {
        return DaoUtil.toPageData(repository.findAll(Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink, authorizationPolicyColumnMap)));
    }
}
