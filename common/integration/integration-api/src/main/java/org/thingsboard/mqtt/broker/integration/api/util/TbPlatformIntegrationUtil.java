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
package org.thingsboard.mqtt.broker.integration.api.util;

import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;

public class TbPlatformIntegrationUtil {

    public static TbPlatformIntegration createPlatformIntegration(IntegrationType type) throws Exception {
        return switch (type) {
            case HTTP ->
                    newInstance("org.thingsboard.mqtt.broker.integration.service.integration.http.HttpIntegration");
            case MQTT ->
                    newInstance("org.thingsboard.mqtt.broker.integration.service.integration.mqtt.MqttIntegration");
            case KAFKA ->
                    newInstance("org.thingsboard.mqtt.broker.integration.service.integration.kafka.KafkaIntegration");
        };
    }

    private static TbPlatformIntegration newInstance(String clazz) throws Exception {
        return newInstance(clazz, null);
    }

    private static TbPlatformIntegration newInstance(String clazz, Object param) throws Exception {
        if (param != null) {
            return (TbPlatformIntegration) Class.forName(clazz).getDeclaredConstructors()[0].newInstance(param);
        } else {
            return (TbPlatformIntegration) Class.forName(clazz).getDeclaredConstructor().newInstance();
        }
    }

}
