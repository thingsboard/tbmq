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
package org.thingsboard.mqtt.broker.dao.client.generic;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Collection;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenericClientSessionCtxServiceImpl implements GenericClientSessionCtxService {

    private final GenericClientSessionCtxDao genericClientSessionCtxDao;

    @Override
    public void saveAllGenericClientSessionCtx(Collection<GenericClientSessionCtx> genericClientSessionContexts) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveAllGenericClientSessionCtx [{}]", genericClientSessionContexts);
        }
        genericClientSessionContexts.forEach(this::validate);
        genericClientSessionCtxDao.saveAll(genericClientSessionContexts);
    }

    @Override
    public GenericClientSessionCtx saveGenericClientSessionCtx(GenericClientSessionCtx genericClientSessionCtx) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveGenericClientSessionCtx [{}]", genericClientSessionCtx);
        }
        validate(genericClientSessionCtx);
        return genericClientSessionCtxDao.save(genericClientSessionCtx);
    }

    @Override
    public void deleteGenericClientSessionCtx(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteGenericClientSessionCtx [{}]", clientId);
        }
        try {
            genericClientSessionCtxDao.remove(clientId);
        } catch (EmptyResultDataAccessException noDataException) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] No session for clientId.", clientId);
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to delete generic client session context.", clientId, e);
        }
    }

    @Override
    public Optional<GenericClientSessionCtx> findGenericClientSessionCtx(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findGenericClientSessionCtx [{}]", clientId);
        }
        return Optional.ofNullable(genericClientSessionCtxDao.findByClientId(clientId));
    }

    @Override
    public ListenableFuture<GenericClientSessionCtx> findGenericClientSessionCtxAsync(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findGenericClientSessionCtxAsync [{}]", clientId);
        }
        return genericClientSessionCtxDao.findByClientIdAsync(clientId);
    }

    private void validate(GenericClientSessionCtx genericClientSessionCtx) {
        if (StringUtils.isEmpty(genericClientSessionCtx.getClientId())) {
            throw new DataValidationException("Client ID should be specified!");
        }
        if (genericClientSessionCtx.getQos2PublishPacketIds() == null) {
            throw new DataValidationException("QoS2 publish packetIds should be specified!");
        }
    }
}
