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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.sun.net.httpserver.HttpServer;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.auth.providers.http.HttpAuthClient;
import org.thingsboard.mqtt.broker.service.auth.providers.http.HttpMqttClientAuthProvider;

import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.service.system.SystemSettingsNotificationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRoutingService;

import java.util.List;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = HttpAuthAdmissionIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class HttpAuthAdmissionIntegrationTestCase extends AbstractPubSubIntegrationTest {
    @Autowired
    private HttpMqttClientAuthProvider provider;
    @Autowired
    private SystemSettingsNotificationService settingsNotifications;
    @Autowired
    private AuthorizationRoutingService routing;
    private HttpServer server;
    private final Semaphore requests = new Semaphore(0);
    private HttpAuthClient httpClient;

    @Before
    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.release();
            byte[] body = "{\"result\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth";
        var config = HttpMqttAuthProviderConfiguration.defaultConfiguration();
        config.setRestEndpointUrl(endpoint);
        config.setReadTimeoutMs(2000);
        config.setMaxParallelRequestsCount(1);
        var stored = getMqttAuthProvider(MqttAuthProviderType.HTTP);
        stored.setConfiguration(config);
        stored.setEnabled(true);
        mqttAuthProviderManagerService.saveAuthProvider(stored);
        // The shared SQL test fixture predates the HTTP provider. Configure the
        // authentication chain through the same notification path as admin updates.
        var authSettings = new MqttAuthSettings();
        authSettings.setPriorities(List.of(MqttAuthProviderType.HTTP));
        settingsNotifications.onMqttAuthSettingUpdate(authSettings);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var active = (HttpAuthClient) ReflectionTestUtils.getField(provider, "client");
            assertThat(active).isNotNull();
            assertThat(ReflectionTestUtils.getField(routing, "priorities"))
                    .isEqualTo(List.of(MqttAuthProviderType.HTTP));
            assertThat(active.getConfig().getRestEndpointUrl()).isEqualTo(endpoint);
        });
        httpClient = (HttpAuthClient) ReflectionTestUtils.getField(provider, "client");
    }

    @After
    public void stopHttpServer() {
        if (server != null) server.stop(0);
    }

    @Test
    public void mqttConnectRejectedByAdmissionMustNotCallHttpAndNextConnectMustRecover() throws Exception {
        connectSuccessfully("http-admission-warmup");
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(httpClient.getSemaphore().availablePermits()).isEqualTo(1);
        // Deterministically model the sole permit owned by an in-flight authentication.
        // Actual MQTT CONNECT still traverses the broker routing/provider/HTTP caller.
        MqttClient rejected = client("http-admission-rejected");
        httpClient.getSemaphore().acquire();
        try {
            Throwable failure = catchThrowable(() -> rejected.connect(options()));
            assertThat(failure).isInstanceOf(MqttException.class);
            assertThat(((MqttException) failure).getReasonCode()).isEqualTo(0x87);
            assertThat(rejected.isConnected()).isFalse();
            assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isFalse();
            assertThat(httpClient.getSemaphore().availablePermits()).isZero();
        } finally {
            httpClient.getSemaphore().release();
            rejected.close();
        }
        connectSuccessfully("http-admission-recovered");
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(httpClient.getSemaphore().availablePermits()).isEqualTo(1);
    }

    private MqttClient client(String id) throws MqttException {
        return new MqttClient(SERVER_URI + mqttPort, id, new MemoryPersistence());
    }

    private MqttConnectionOptions options() {
        var options = new MqttConnectionOptions();
        options.setConnectionTimeout(10);
        options.setCleanStart(true);
        options.setSessionExpiryInterval(0L);
        return options;
    }

    private void connectSuccessfully(String id) throws MqttException {
        MqttClient mqtt = client(id);
        try {
            mqtt.connect(options());
            assertThat(mqtt.isConnected()).isTrue();
            mqtt.disconnect();
        } finally {
            mqtt.close();
        }
    }
}
