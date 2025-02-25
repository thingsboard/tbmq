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
package org.thingsboard.mqtt.broker.integration.service.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatisticsService;
import org.thingsboard.mqtt.broker.integration.api.util.LogSettingsComponent;
import org.thingsboard.mqtt.broker.integration.service.api.IntegrationRateLimitService;
import org.thingsboard.mqtt.broker.integration.service.processing.IntegrationMsgProcessor;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.EventStorageService;
import org.thingsboard.mqtt.broker.service.IntegrationContextProvider;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
public class TbIntegrationExecutorContextProvider implements IntegrationContextProvider {

    private final IntegrationMsgProcessor integrationMsgProcessor;
    private final Optional<IntegrationStatisticsService> statisticsService;
    private final LogSettingsComponent logSettingsComponent;
    private final SharedEventLoopGroupService sharedEventLoopGroupService;
    private final EventStorageService eventStorageService;
    private final IntegrationRateLimitService rateLimitService;
    private final ServiceInfoProvider serviceInfoProvider;

    @Override
    public IntegrationContext buildIntegrationContext(IntegrationLifecycleMsg lifecycleMsg) {
        return doBuildIntegrationContext(lifecycleMsg, null);
    }

    @Override
    public IntegrationContext buildIntegrationContext(Integration integration, BasicCallback callback) {
        return doBuildIntegrationContext(IntegrationLifecycleMsg.fromIntegration(integration), callback);
    }

    private TbIntegrationExecutorIntegrationContext doBuildIntegrationContext(IntegrationLifecycleMsg lifecycleMsg, BasicCallback callback) {
        return new TbIntegrationExecutorIntegrationContext(lifecycleMsg, integrationMsgProcessor, getStatisticsService(),
                logSettingsComponent, sharedEventLoopGroupService, eventStorageService, rateLimitService,
                serviceInfoProvider.getServiceId(), callback);
    }

    private IntegrationStatisticsService getStatisticsService() {
        return statisticsService.orElse(null);
    }

}
