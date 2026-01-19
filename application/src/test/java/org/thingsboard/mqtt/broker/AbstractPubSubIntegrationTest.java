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
package org.thingsboard.mqtt.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.service.mqtt.auth.MqttAuthProviderManagerService;
import org.thingsboard.mqtt.broker.service.testing.integration.executor.ExternalExecutorService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@ActiveProfiles("test")
@Import(AbstractPubSubIntegrationTest.KafkaTestContainersConfiguration.class)
@ComponentScan({"org.thingsboard.mqtt.broker"})
@WebAppConfiguration
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
public abstract class AbstractPubSubIntegrationTest {

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

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty("tbmq.graceful.shutdown.timeout.sec", "1");
    }

    @Before
    public void beforeTest() throws Exception {
        resetMqttAuthProvidersToDefaultConfiguration();
    }

    @Autowired
    @Lazy
    protected BCryptPasswordEncoder passwordEncoder;
    @Autowired
    protected MqttClientCredentialsService mqttClientCredentialsService;
    @Autowired
    protected MqttAuthProviderService mqttAuthProviderService;
    @Autowired
    protected MqttAuthProviderManagerService mqttAuthProviderManagerService;
    @Autowired
    @Lazy
    protected ExternalExecutorService externalExecutorService;

    @Value("${listener.tcp.bind_port}")
    protected int mqttPort;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class TestPublishMsg {
        public int publisherId;
        public int sequenceId;
        public boolean isLast;
    }

    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.2"));

    @ClassRule
    public static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:8.0")
            .withExposedPorts(6379)
            .withLogConsumer(x -> log.warn("{}", x.getUtf8StringWithoutLineEnding()))
            .withCommand("valkey-server", "--requirepass", "password");

    @ClassRule
    public static ExternalResource resource = new ExternalResource() {
        @Override
        protected void before() {
            valkey.start();
            System.setProperty("redis.connection.type", "standalone");
            System.setProperty("redis.standalone.host", valkey.getHost());
            System.setProperty("redis.standalone.port", String.valueOf(valkey.getMappedPort(6379)));
            System.setProperty("redis.password", "password");
        }

        @Override
        protected void after() {
            valkey.stop();
            List.of("redis.connection.type", "redis.standalone.host", "redis.standalone.port", "redis.password")
                    .forEach(System.getProperties()::remove);
        }
    };

    public static class ReplaceKafkaPropertiesBeanPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
            if (bean instanceof TbKafkaConsumerSettings kafkaSettings) {
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            if (bean instanceof TbKafkaProducerSettings kafkaSettings) {
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            if (bean instanceof TbKafkaAdminSettings kafkaAdminSettings) {
                kafkaAdminSettings.setServers(kafka.getBootstrapServers());
            }
            return bean;
        }
    }

    @TestConfiguration
    static class KafkaTestContainersConfiguration {
        @Bean
        ReplaceKafkaPropertiesBeanPostProcessor beanPostProcessor() {
            return new ReplaceKafkaPropertiesBeanPostProcessor();
        }
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

    protected MqttAuthProvider getMqttAuthProvider(MqttAuthProviderType type) {
        return mqttAuthProviderService.getAuthProviderByType(type)
                .orElseThrow(() -> new IllegalStateException("No " + type.getDisplayName() + " provider found!"));
    }

    protected void enableBasicProvider() {
        enableProvider(MqttAuthProviderType.MQTT_BASIC);
    }

    protected void enabledScramProvider() {
        enableProvider(MqttAuthProviderType.SCRAM);
    }

    private void enableProvider(MqttAuthProviderType type) {
        MqttAuthProvider provider = getMqttAuthProvider(type);
        if (!provider.isEnabled()) {
            mqttAuthProviderManagerService.enableAuthProvider(provider.getId());
        }
    }

    private void resetMqttAuthProvidersToDefaultConfiguration() {
        Arrays.stream(MqttAuthProviderType.values()).forEach(this::resetMqttAuthProviderToDefaultConfiguration);
    }

    private void resetMqttAuthProviderToDefaultConfiguration(MqttAuthProviderType type) {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(type);
        mqttAuthProvider.setEnabled(false);
        switch (type) {
            case MQTT_BASIC ->
                    mqttAuthProvider.setConfiguration(MqttAuthProvider.defaultBasicAuthProvider(false).getConfiguration());
            case X_509 ->
                    mqttAuthProvider.setConfiguration(MqttAuthProvider.defaultSslAuthProvider(false).getConfiguration());
            case JWT ->
                    mqttAuthProvider.setConfiguration(MqttAuthProvider.defaultJwtAuthProvider(false).getConfiguration());
            case SCRAM ->
                    mqttAuthProvider.setConfiguration(MqttAuthProvider.defaultScramAuthProvider(false).getConfiguration());
            case HTTP ->
                    mqttAuthProvider.setConfiguration(MqttAuthProvider.defaultHttpAuthProvider(false).getConfiguration());
        }
        mqttAuthProviderManagerService.saveAuthProvider(mqttAuthProvider);
    }

}
