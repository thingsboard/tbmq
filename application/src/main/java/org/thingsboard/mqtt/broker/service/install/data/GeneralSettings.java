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
package org.thingsboard.mqtt.broker.service.install.data;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

public class GeneralSettings {

    public static AdminSettings createDefaults() {
        AdminSettings generalSettings = new AdminSettings();
        generalSettings.setKey(SysAdminSettingType.GENERAL.getKey());
        ObjectNode node = JacksonUtil.newObjectNode();
        node.put("baseUrl", "http://localhost:8083");
        node.put("prohibitDifferentUrl", false);
        generalSettings.setJsonValue(node);
        return generalSettings;
    }
}
