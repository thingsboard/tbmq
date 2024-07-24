/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.install.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;

import java.util.List;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultDataUpdateService implements DataUpdateService {

    private static final int DEFAULT_PAGE_SIZE = 1024;

    private final MqttClientCredentialsService mqttClientCredentialsService;

    @Override
    public void updateData(String fromVersion) throws Exception {
        switch (fromVersion) {
            case "1.3.0":
                log.info("Updating data from version 1.3.0 to 1.3.1 ...");
                updateSslMqttClientCredentials();
                break;
            default:
                throw new RuntimeException("Unable to update data, unsupported fromVersion: " + fromVersion);
        }
    }

    private void updateSslMqttClientCredentials() {
        List<MqttClientCredentials> sslCredentials = mqttClientCredentialsService.findByCredentialsType(ClientCredentialsType.SSL);
        log.info("Found {} client credentials with type {}", sslCredentials.size(), ClientCredentialsType.SSL);
        if (!CollectionUtils.isEmpty(sslCredentials)) {
            for (MqttClientCredentials credentials : sslCredentials) {
                String newCredentialsValueStr = convertSslMqttClientCredentialsForVersion131(credentials.getCredentialsValue());

                if (newCredentialsValueStr == null) {
                    continue;
                }

                credentials.setCredentialsValue(newCredentialsValueStr);
                MqttClientCredentials savedMqttClientCredentials = mqttClientCredentialsService.saveCredentials(credentials);
                log.info("Updated client credentials {}", savedMqttClientCredentials);
            }
        }
    }

    String convertSslMqttClientCredentialsForVersion131(String oldCredentialsValueStr) {
        ObjectNode newCredentialsValue = JacksonUtil.newObjectNode();
        JsonNode oldCredentialsValue = JacksonUtil.toJsonNode(oldCredentialsValueStr);

        if (oldCredentialsValue.has("certCommonName")) {
            String certCommonName = oldCredentialsValue.get("certCommonName").asText();
            newCredentialsValue.put("certCnPattern", certCommonName);
            newCredentialsValue.put("certCnIsRegex", false);

            if (oldCredentialsValue.has("authRulesMapping")) {
                JsonNode authRulesMapping = oldCredentialsValue.get("authRulesMapping");
                newCredentialsValue.set("authRulesMapping", authRulesMapping);
            }
            return JacksonUtil.toString(newCredentialsValue);
        } else {
            log.info("Skipping client credentials transform with value {}", oldCredentialsValueStr);
        }
        return null;
    }

}
