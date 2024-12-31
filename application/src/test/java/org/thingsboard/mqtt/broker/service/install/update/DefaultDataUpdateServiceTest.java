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
package org.thingsboard.mqtt.broker.service.install.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DefaultDataUpdateService.class)
public class DefaultDataUpdateServiceTest {

    @MockBean
    MqttClientCredentialsService mqttClientCredentialsService;
    @MockBean
    AdminSettingsService adminSettingsService;

    @SpyBean
    DefaultDataUpdateService service;

    @Before
    public void setUp() throws Exception {
        willCallRealMethod().given(service).convertSslMqttClientCredentialsForVersion140(any());
    }

    @Test
    public void convertSslMqttClientCredentialsForVersion140FirstRun() throws IOException {
        JsonNode spec = readFromResource("update/131/ssl_mqtt_creds_in.json");
        JsonNode expected = readFromResource("update/131/ssl_mqtt_creds_out.json");

        String credentialsValue = service.convertSslMqttClientCredentialsForVersion140(spec.get("credentialsValue").asText());
        assertThat(credentialsValue).isNotNull();

        ObjectNode converted = (ObjectNode) spec;

        converted.remove("credentialsValue");
        converted.put("credentialsValue", credentialsValue);

        assertThat(converted.toPrettyString().replaceAll("\\s+", ""))
                .isEqualTo(expected.toPrettyString().replaceAll("\\s+", "")); // use IDE feature <Click to see difference>
    }

    @Test
    public void convertSslMqttClientCredentialsForVersion140SecondRun() throws IOException {
        JsonNode spec = readFromResource("update/131/ssl_mqtt_creds_out.json");

        String credentialsValue = service.convertSslMqttClientCredentialsForVersion140(spec.get("credentialsValue").asText());
        assertThat(credentialsValue).isNull();
    }

    JsonNode readFromResource(String resourceName) throws IOException {
        return JacksonUtil.OBJECT_MAPPER.readTree(this.getClass().getClassLoader().getResourceAsStream(resourceName));
    }

}
