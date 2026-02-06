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
package org.thingsboard.mqtt.broker.dao.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.connectivity.ConnectivityInfo;

import java.util.Collections;
import java.util.Map;

public class AdminSettingsUtil {

    private AdminSettingsUtil() {
    }

    public static Map<String, ConnectivityInfo> convertConnectivitySettings(AdminSettings connectivity) {
        return connectivity == null ? Collections.emptyMap() :
                JacksonUtil.convertValue(connectivity.getJsonValue(), new TypeReference<>() {
                });
    }

    public static String constructWsConnectionUrl(AdminSettings connectivity) {
        Map<String, ConnectivityInfo> connectivityMap = convertConnectivitySettings(connectivity);
        ConnectivityInfo connectivityInfo = connectivityMap.get(BrokerConstants.WS_CONNECTIVITY);
        if (connectivityInfo != null) {
            String host = connectivityInfo.getHost();
            if (StringUtils.isEmpty(host)) {
                host = "localhost";
            }

            String port = connectivityInfo.getPort();
            if (StringUtils.isEmpty(port)) {
                port = "8084";
            }
            return "ws://" + host + BrokerConstants.COLON + port + BrokerConstants.WS_PATH;
        }
        return "ws://localhost:8084/mqtt";
    }

}
