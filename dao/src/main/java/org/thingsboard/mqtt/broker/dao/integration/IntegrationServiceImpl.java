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
package org.thingsboard.mqtt.broker.dao.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.List;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validateId;
import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {

    private static final String INCORRECT_INTEGRATION_ID = "Incorrect integrationId ";

    private final IntegrationDao integrationDao;
    private final TopicValidationService topicValidationService;

    @Override
    public Integration saveIntegration(Integration integration) {
        log.trace("Executing saveIntegration [{}]", integration);
        integrationValidator.validate(integration);
        integration.setDisconnectedTime(integration.isEnabled() ? 0 : System.currentTimeMillis());
        return integrationDao.save(integration);
    }

    @Override
    public void saveIntegrationStatus(Integration integration, ObjectNode status) {
        log.trace("Executing saveIntegrationStatus [{}][{}]", integration, status);
        integration.setStatus(status);
        integrationDao.save(integration);
    }

    @Override
    public Integration findIntegrationById(UUID integrationId) {
        log.trace("Executing findIntegrationById [{}]", integrationId);
        validateId(integrationId, id -> INCORRECT_INTEGRATION_ID + id);
        return integrationDao.findById(integrationId);
    }

    @Override
    public ListenableFuture<Integration> findIntegrationByIdAsync(UUID integrationId) {
        log.trace("Executing findIntegrationByIdAsync [{}]", integrationId);
        validateId(integrationId, id -> INCORRECT_INTEGRATION_ID + id);
        return integrationDao.findByIdAsync(integrationId);
    }

    @Override
    public List<Integration> findAllIntegrations() {
        log.trace("Executing findAllIntegrations");
        return integrationDao.find();
    }

    @Override
    public PageData<Integration> findIntegrations(PageLink pageLink) {
        log.trace("Executing findIntegrations, pageLink [{}]", pageLink);
        validatePageLink(pageLink);
        return integrationDao.findIntegrations(pageLink);
    }

    @Override
    @Transactional
    public boolean deleteIntegration(Integration integration) {
        log.trace("Executing deleteIntegration [{}]", integration.getId());
        return integrationDao.removeById(integration.getId());
    }

    private final DataValidator<Integration> integrationValidator =
            new DataValidator<>() {

                @Override
                protected void validateCreate(Integration integration) {
                    Integration existingIntegration = integrationDao.findIntegrationByName(integration.getName());
                    if (existingIntegration != null) {
                        throw new DataValidationException("Integration with such name already exists!");
                    }
                }

                @Override
                protected void validateUpdate(Integration integration) {
                    Integration existingIntegration = integrationDao.findById(integration.getId());
                    if (existingIntegration == null) {
                        throw new DataValidationException("Unable to update non-existent Integration!");
                    }
                    if (!existingIntegration.getType().equals(integration.getType())) {
                        throw new DataValidationException("Integration type can not be changed!");
                    }
                    Integration integrationByName = integrationDao.findIntegrationByName(integration.getName());
                    if (integrationByName != null && !integrationByName.getId().equals(existingIntegration.getId())) {
                        throw new DataValidationException("Integration with such name already exists!");
                    }
                }

                @Override
                protected void validateDataImpl(Integration integration) {
                    if (StringUtils.isEmpty(integration.getName())) {
                        throw new DataValidationException("Integration name should be specified!");
                    }
                    if (integration.getType() == null) {
                        throw new DataValidationException("Integration type should be specified!");
                    }
                    JsonNode topicFilters = integration.getConfiguration().get("topicFilters");
                    if (topicFilters == null || topicFilters.isNull()) {
                        throw new DataValidationException("Topic filters should be specified!");
                    }
                    if (!topicFilters.isArray()) {
                        throw new DataValidationException("Topic filters should be an array!");
                    }
                    topicFilters.forEach(topicFilter -> topicValidationService.validateTopicFilter(topicFilter.asText()));
                }
            };

}
