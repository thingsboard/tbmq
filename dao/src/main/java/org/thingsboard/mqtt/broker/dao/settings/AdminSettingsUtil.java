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

        ConnectivityInfo wssInfo = connectivityMap.get(BrokerConstants.WSS_CONNECTIVITY);
        if (wssInfo != null && wssInfo.isEnabled()) {
            return buildWsUrl("wss", wssInfo, "localhost", "8085");
        }

        ConnectivityInfo wsInfo = connectivityMap.get(BrokerConstants.WS_CONNECTIVITY);
        if (wsInfo != null && wsInfo.isEnabled()) {
            return buildWsUrl("ws", wsInfo, "localhost", "8084");
        }

        return "ws://localhost:8084/mqtt";
    }

    private static String buildWsUrl(String scheme, ConnectivityInfo info, String defaultHost, String defaultPort) {
        String host = StringUtils.isEmpty(info.getHost()) ? defaultHost : info.getHost();
        String port = StringUtils.isEmpty(info.getPort()) ? defaultPort : info.getPort();
        return scheme + "://" + host + BrokerConstants.COLON + port + BrokerConstants.WS_PATH;
    }

}
