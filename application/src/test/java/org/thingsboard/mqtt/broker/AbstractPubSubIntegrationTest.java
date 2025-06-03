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
package org.thingsboard.mqtt.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.service.mqtt.auth.MqttAuthProviderManagerService;
import org.thingsboard.mqtt.broker.service.testing.integration.executor.ExternalExecutorService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public abstract class AbstractPubSubIntegrationTest extends AbstractIntegrationTest {

    public static final int ONE_HOUR_MS = 3600000;
    public static final String LOCALHOST = "localhost";
    public static final String SERVER_URI = "tcp://" + LOCALHOST + BrokerConstants.COLON;
    public static final byte[] PAYLOAD = "testPayload".getBytes(StandardCharsets.UTF_8);

    public static final List<UserProperty> USER_PROPERTIES = List.of(
            new UserProperty("myUserPropertyKey", "myUserPropertyValue"),
            new UserProperty("region", "UA"),
            new UserProperty("type", "JSON")
    );
    public static final MqttProperties MQTT_PROPERTIES;

    static {
        MQTT_PROPERTIES = new MqttProperties();
        MQTT_PROPERTIES.setUserProperties(USER_PROPERTIES);
        MQTT_PROPERTIES.setWillDelayInterval(1L);
    }

    protected final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    @Lazy
    protected BCryptPasswordEncoder passwordEncoder;
    @Autowired
    protected MqttAuthProviderService mqttAuthProviderService;
    @Autowired
    protected MqttAuthProviderManagerService mqttAuthProviderManagerService;
    @Autowired
    @Lazy
    protected ExternalExecutorService externalExecutorService;

    @Value("${listener.tcp.bind_port}")
    protected int mqttPort;

    @Before
    public void beforeTest() throws Exception {
        disableBasicProvider();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class TestPublishMsg {
        public int publisherId;
        public int sequenceId;
        public boolean isLast;
    }

    @NotNull
    protected MqttConnectionOptions getOptions(String username) {
        return getOptions(true, username);
    }

    @NotNull
    protected MqttConnectionOptions getOptions(boolean cleanStart, String username) {
        MqttConnectionOptions pubOptions = new MqttConnectionOptions();
        pubOptions.setCleanStart(cleanStart);
        pubOptions.setUserName(username);
        return pubOptions;
    }

    protected void enabledBasicProvider() {
        enableProvider(MqttAuthProviderType.BASIC);
    }

    protected void enabledScramProvider() {
        enableProvider(MqttAuthProviderType.SCRAM);
    }

    protected void disableBasicProvider() {
        disableProvider(MqttAuthProviderType.BASIC);
    }

    private void disableProvider(MqttAuthProviderType type) {
        MqttAuthProvider provider = getOrCreateMqttAuthProvider(type);
        if (provider.isEnabled()) {
            mqttAuthProviderManagerService.disableAuthProvider(provider.getId());
        }
    }

    private void enableProvider(MqttAuthProviderType type) {
        MqttAuthProvider provider = getOrCreateMqttAuthProvider(type);
        if (!provider.isEnabled()) {
            mqttAuthProviderManagerService.enableAuthProvider(provider.getId());
        }
    }

    private MqttAuthProvider getOrCreateMqttAuthProvider(MqttAuthProviderType type) {
        Optional<MqttAuthProvider> providerOpt = mqttAuthProviderService.getAuthProviderByType(type);
        return providerOpt.orElseGet(() -> {
            MqttAuthProvider authProvider = switch (type) {
                case BASIC -> MqttAuthProvider.defaultBasicAuthProvider();
                case X_509 -> MqttAuthProvider.defaultSslAuthProvider();
                case JWT -> MqttAuthProvider.defaultJwtAuthProvider();
                case SCRAM -> MqttAuthProvider.defaultScramAuthProvider();
            };
            return mqttAuthProviderManagerService.saveAuthProvider(authProvider);
        });
    }

}
