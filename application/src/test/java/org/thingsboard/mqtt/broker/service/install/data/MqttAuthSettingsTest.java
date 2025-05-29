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
package org.thingsboard.mqtt.broker.service.install.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MqttAuthSettingsTest {

    @Test
    public void testCreateDefaults() {
        // When
        AdminSettings adminSettings = MqttAuthSettings.createDefaults();

        // Then
        assertThat(adminSettings).isNotNull();
        assertThat(adminSettings.getKey())
                .isEqualTo(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());

        JsonNode jsonValue = adminSettings.getJsonValue();
        assertThat(jsonValue).isNotNull();
        assertThat(jsonValue.has("useListenerBasedProviderOnly")).isTrue();
        assertThat(jsonValue.get("useListenerBasedProviderOnly").asBoolean()).isFalse();
        assertThat(jsonValue.has("priorities")).isTrue();
        JsonNode prioritiesNode = jsonValue.get("priorities");
        assertThat(prioritiesNode.isArray()).isTrue();

        // Verify priority list matches default enum order
        List<MqttAuthProviderType> expectedPriorities = MqttAuthProviderType.getDefaultPriorityList();
        List<MqttAuthProviderType> actualPriorities = JacksonUtil.convertValue(prioritiesNode, new TypeReference<>() {});
        assertThat(actualPriorities).containsExactlyElementsOf(expectedPriorities);

    }

}