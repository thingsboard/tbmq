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
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationSessionCtxServiceImpl implements ApplicationSessionCtxService {

    private final ApplicationSessionCtxDao applicationSessionCtxDao;

    @Override
    public ApplicationSessionCtx saveApplicationSessionCtx(ApplicationSessionCtx applicationSessionCtx) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveApplicationSessionCtx [{}]", applicationSessionCtx);
        }
        validate(applicationSessionCtx);
        return applicationSessionCtxDao.save(applicationSessionCtx);
    }

    @Override
    public void deleteApplicationSessionCtx(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteApplicationSessionCtx [{}]", clientId);
        }
        applicationSessionCtxDao.remove(clientId);
    }

    @Override
    public Optional<ApplicationSessionCtx> findApplicationSessionCtx(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findApplicationSessionCtx [{}]", clientId);
        }
        return Optional.ofNullable(applicationSessionCtxDao.findByClientId(clientId));
    }

    @Override
    public ListenableFuture<ApplicationSessionCtx> findApplicationSessionCtxAsync(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findApplicationSessionCtxAsync [{}]", clientId);
        }
        return applicationSessionCtxDao.findByClientIdAsync(clientId);
    }

    private void validate(ApplicationSessionCtx applicationSessionCtx) {
        if (StringUtils.isEmpty(applicationSessionCtx.getClientId())) {
            throw new DataValidationException("Client ID should be specified!");
        }
        if (applicationSessionCtx.getPublishMsgInfos() == null) {
            throw new DataValidationException("Published messages infos should be specified!");
        }
    }
}
