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
package org.thingsboard.mqtt.broker.dao.client.generic;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.GenericClientSessionCtxEntity;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericClientSessionCtxDaoImpl implements GenericClientSessionCtxDao {
    private final GenericClientSessionCtxRepository genericClientSessionCtxRepository;
    private final InsertGenericClientSessionCtxRepository insertGenericClientSessionCtxRepository;

    @Override
    public void saveAll(Collection<GenericClientSessionCtx> genericClientSessionContexts) {
        List<GenericClientSessionCtxEntity> entities = genericClientSessionContexts.stream().map(GenericClientSessionCtxEntity::new).collect(Collectors.toList());
        insertGenericClientSessionCtxRepository.saveOrUpdate(entities);
    }

    @Override
    public GenericClientSessionCtx save(GenericClientSessionCtx genericClientSessionCtx) {
        return DaoUtil.getData(genericClientSessionCtxRepository.save(new GenericClientSessionCtxEntity(genericClientSessionCtx)));
    }

    @Override
    public GenericClientSessionCtx find(String clientId) {
        return DaoUtil.getData(genericClientSessionCtxRepository.findByClientId(clientId));
    }

    @Override
    public List<GenericClientSessionCtx> find() {
        List<GenericClientSessionCtxEntity> entities = Lists.newArrayList(genericClientSessionCtxRepository.findAll());
        return DaoUtil.convertDataList(entities);
    }

    @Override
    public void remove(String clientId) {
        genericClientSessionCtxRepository.deleteById(clientId);
    }
}
