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

import io.netty.channel.EventLoopGroup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatisticsService;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.api.util.LogSettingsComponent;
import org.thingsboard.mqtt.broker.integration.service.api.IntegrationRateLimitService;
import org.thingsboard.mqtt.broker.integration.service.processing.IntegrationMsgProcessor;
import org.thingsboard.mqtt.broker.service.EventStorageService;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Getter
public class TbIntegrationExecutorIntegrationContext implements IntegrationContext {

    private final IntegrationLifecycleMsg lifecycleMsg;
    private final IntegrationMsgProcessor integrationMsgProcessor;
    private final IntegrationStatisticsService statisticsService;
    private final LogSettingsComponent logSettingsComponent;
    private final SharedEventLoopGroupService sharedEventLoopGroupService;
    private final EventStorageService eventStorageService;
    private final IntegrationRateLimitService rateLimitService;
    private final String serviceId;
    private final BasicCallback callback;

    @Override
    public EventLoopGroup getSharedEventLoop() {
        return sharedEventLoopGroupService.getSharedEventLoopGroup();
    }

    @Override
    public boolean isExceptionStackTraceEnabled() {
        return logSettingsComponent.isExceptionStackTraceEnabled();
    }

    @Override
    public void startProcessingIntegrationMessages(TbPlatformIntegration integration) {
        integrationMsgProcessor.startProcessingIntegrationMessages(integration);
    }

    @Override
    public void stopProcessingPersistedMessages(String id) {
        integrationMsgProcessor.stopProcessingPersistedMessages(id);
    }

    @Override
    public void clearIntegrationMessages(String id) {
        integrationMsgProcessor.clearIntegrationMessages(id);
    }

    @Override
    public void onUplinkMessageProcessed(boolean success) {
        if (lifecycleMsg != null && statisticsService != null) {
            statisticsService.onUplinkMsg(lifecycleMsg.getType(), success);
        }
    }

    @Override
    public void saveErrorEvent(ErrorEvent errorEvent) {
        UUID integrationId = lifecycleMsg.getIntegrationId();

        if (!rateLimitService.checkLimit(integrationId, false)) {
            if (rateLimitService.alreadyProcessed(integrationId)) {
                log.trace("[{}] Rate limited error event already sent", integrationId);
                return;
            } else {
                errorEvent.setError("Integration error rate limits reached!");
            }
        }

        eventStorageService.persistError(integrationId, errorEvent);
    }

    @Override
    public BasicCallback getCallback() {
        return callback != null ? callback : CallbackUtil.EMPTY;
    }
}
