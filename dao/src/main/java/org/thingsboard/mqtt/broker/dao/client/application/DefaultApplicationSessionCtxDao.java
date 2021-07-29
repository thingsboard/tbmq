/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.ApplicationSessionCtxEntity;

import java.util.List;

@Slf4j
@Component
// TODO: make some methods async to properly work
public class DefaultApplicationSessionCtxDao implements ApplicationSessionCtxDao {
    @Autowired
    private ApplicationSessionCtxRepository applicationSessionCtxRepository;

    @Override
    public ApplicationSessionCtx save(ApplicationSessionCtx applicationSessionCtx) {
        return DaoUtil.getData(applicationSessionCtxRepository.save(new ApplicationSessionCtxEntity(applicationSessionCtx)));
    }

    @Override
    public ApplicationSessionCtx find(String clientId) {
        return DaoUtil.getData(applicationSessionCtxRepository.findByClientId(clientId));
    }

    @Override
    public List<ApplicationSessionCtx> find() {
        List<ApplicationSessionCtxEntity> entities = Lists.newArrayList(applicationSessionCtxRepository.findAll());
        return DaoUtil.convertDataList(entities);
    }

    @Override
    public void remove(String clientId) {
        applicationSessionCtxRepository.deleteById(clientId);
    }
}
