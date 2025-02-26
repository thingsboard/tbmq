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
package org.thingsboard.mqtt.broker.integration.api;

import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.integration.api.stats.IntegrationProcessorStats;

import java.util.UUID;

public interface IntegrationStatisticsService {

    IntegrationProcessorStats createIntegrationProcessorStats(UUID integrationId);

    void clearIntegrationProcessorStats(UUID integrationId);

    MessagesStats createIeUplinkPublishStats();

    void onIntegrationStateUpdate(IntegrationType integrationType, ComponentLifecycleEvent state, boolean success);

    void onIntegrationsCountUpdate(IntegrationType integrationType, int started, int failed);

    void onUplinkMsg(IntegrationType integrationType, boolean success);

    void onDownlinkMsg(IntegrationType integrationType, boolean success);

    void printStats();

    void reset();

}
