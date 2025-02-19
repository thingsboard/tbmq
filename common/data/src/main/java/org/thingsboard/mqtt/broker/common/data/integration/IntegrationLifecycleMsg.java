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
package org.thingsboard.mqtt.broker.common.data.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.STARTED;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
// This object is not yet used in any case where EqualsAndHashCode can be useful
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IntegrationLifecycleMsg {

    @EqualsAndHashCode.Include
    private UUID integrationId;
    private IntegrationType type;
    private String name;
    private boolean enabled;
    private JsonNode configuration;
    private ComponentLifecycleEvent event;

    public static IntegrationLifecycleMsg fromIntegration(Integration integration) {
        return new IntegrationLifecycleMsg(integration.getId(), integration.getType(), integration.getName(),
                integration.isEnabled(), integration.getConfiguration(), STARTED);
    }
}
