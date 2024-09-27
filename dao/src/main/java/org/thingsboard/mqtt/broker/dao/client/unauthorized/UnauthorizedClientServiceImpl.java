/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;

import java.util.Optional;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnauthorizedClientServiceImpl implements UnauthorizedClientService {

    public static final String RANDOM_DELAY_INTERVAL_MS_EXPRESSION =
            "#{T(org.apache.commons.lang3.RandomUtils).nextLong(0, ${sql.ttl.unauthorized_client.execution_interval_ms})}";

    @Value("${sql.ttl.unauthorized_client.ttl:259200}")
    private long ttlInSec;
    @Value("${sql.ttl.unauthorized_client.enabled:true}")
    private boolean ttlTaskExecutionEnabled;

    private final UnauthorizedClientDao unauthorizedClientDao;

    @Override
    public ListenableFuture<Void> save(UnauthorizedClient unauthorizedClient) {
        if (log.isTraceEnabled()) {
            log.trace("Executing save unauthorized client [{}]", unauthorizedClient);
        }
        validate(unauthorizedClient);
        return unauthorizedClientDao.save(unauthorizedClient);
    }

    @Override
    public ListenableFuture<Void> remove(UnauthorizedClient unauthorizedClient) {
        if (log.isTraceEnabled()) {
            log.trace("Executing remove unauthorized client [{}]", unauthorizedClient);
        }
        validateClientIdPresent(unauthorizedClient);
        return unauthorizedClientDao.remove(unauthorizedClient);
    }

    @Override
    public void deleteUnauthorizedClient(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteUnauthorizedClient [{}]", clientId);
        }
        try {
            unauthorizedClientDao.deleteById(clientId);
        } catch (Exception e) {
            log.warn("[{}] Failed to delete unauthorized client.", clientId, e);
        }
    }

    @Override
    public void deleteAllUnauthorizedClients() {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteAllUnauthorizedClients");
        }
        try {
            unauthorizedClientDao.deleteAll();
        } catch (Exception e) {
            log.warn("Failed to delete all unauthorized clients.", e);
        }
    }

    @Override
    public Optional<UnauthorizedClient> findUnauthorizedClient(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUnauthorizedClient [{}]", clientId);
        }
        return Optional.ofNullable(unauthorizedClientDao.findByClientId(clientId));
    }

    @Override
    public PageData<UnauthorizedClient> findUnauthorizedClients(UnauthorizedClientQuery query) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUnauthorizedClients, query [{}]", query);
        }
        validatePageLink(query.getPageLink());
        return unauthorizedClientDao.findAll(query);
    }

    @Override
    public PageData<UnauthorizedClient> findUnauthorizedClients(PageLink pageLink) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUnauthorizedClients, pageLink [{}]", pageLink);
        }
        validatePageLink(pageLink);
        return unauthorizedClientDao.findAll(pageLink);
    }

    @Override
    public void cleanupUnauthorizedClients(long ttl) {
        if (log.isTraceEnabled()) {
            log.trace("Executing cleanupUnauthorizedClients, ttl [{}]", ttl);
        }
        if (ttl <= 0) {
            log.info("System TTL should be greater than 0 to clean up unauthorized clients data!");
            return;
        }
        unauthorizedClientDao.cleanupUnauthorizedClients(ttl);
    }

    private void validate(UnauthorizedClient unauthorizedClient) {
        validateClientIdPresent(unauthorizedClient);
        if (StringUtils.isEmpty(unauthorizedClient.getIpAddress())) {
            throw new DataValidationException("Ip address should be specified!");
        }
        if (unauthorizedClient.getTs() == null) {
            throw new DataValidationException("Timestamp should be specified!");
        }
        if (StringUtils.isEmpty(unauthorizedClient.getReason())) {
            throw new DataValidationException("Reason should be specified!");
        }
    }

    private void validateClientIdPresent(UnauthorizedClient unauthorizedClient) {
        if (StringUtils.isEmpty(unauthorizedClient.getClientId())) {
            throw new DataValidationException("Client ID should be specified!");
        }
    }

    @Scheduled(initialDelayString = RANDOM_DELAY_INTERVAL_MS_EXPRESSION, fixedDelayString = "${sql.ttl.unauthorized_client.execution_interval_ms}")
    public void cleanUp() {
        if (ttlTaskExecutionEnabled) {
            cleanupUnauthorizedClients(ttlInSec);
        }
    }
}
