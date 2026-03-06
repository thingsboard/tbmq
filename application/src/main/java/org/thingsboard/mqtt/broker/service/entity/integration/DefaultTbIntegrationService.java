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
package org.thingsboard.mqtt.broker.service.entity.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.integration.PlatformIntegrationService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbIntegrationService extends AbstractTbEntityService implements TbIntegrationService {

    private final IntegrationService integrationService;
    private final PlatformIntegrationService platformIntegrationService;
    private final RateLimitService rateLimitService;

    @Override
    public Integration save(Integration integration, User currentUser) {
        boolean created = integration.getId() == null;
        Integration result = integrationService.saveIntegration(integration);
        platformIntegrationService.processIntegrationUpdate(result, created);
        return result;
    }

    @Override
    public void delete(Integration integration, User currentUser) {
        boolean removed = integrationService.deleteIntegration(integration);
        if (removed) {
            rateLimitService.decrementApplicationClientsCount();
        }
        platformIntegrationService.processIntegrationDelete(integration, removed);
    }

    @Override
    public void restart(Integration integration, User currentUser) throws ThingsboardException {
        platformIntegrationService.processIntegrationRestart(integration);
    }

}
