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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class MqttAuthSettings implements Serializable {

    @Serial
    private static final long serialVersionUID = -8045245463193283033L;

    private boolean useListenerBasedProviderOnly;
    private List<MqttAuthProviderType> priorities;

    public static AdminSettings createDefaults() {
        MqttAuthSettings mqttAuthSettings = new MqttAuthSettings();
        mqttAuthSettings.setUseListenerBasedProviderOnly(false);
        mqttAuthSettings.setPriorities(MqttAuthProviderType.getDefaultPriorityList());

        AdminSettings adminSettings = new AdminSettings();
        adminSettings.setKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        adminSettings.setJsonValue(JacksonUtil.valueToTree(mqttAuthSettings));
        return adminSettings;
    }

    public static MqttAuthSettings fromJsonValue(JsonNode jsonValue) {
        return JacksonUtil.toValue(jsonValue, MqttAuthSettings.class);
    }
}
