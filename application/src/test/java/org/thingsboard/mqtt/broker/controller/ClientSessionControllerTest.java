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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPageInfosImpl;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DaoSqlTest
public class ClientSessionControllerTest extends AbstractControllerTest {

    private static final String BY_NODE_URL = "/api/client-session/info/by-node";
    private static final String CONNECTED_CLIENT = "by_node_connected";
    private static final String PERSISTED_CLIENT = "by_node_persisted";
    private static final String APPLICATION_CLIENT = "by_node_application";

    @Autowired
    private ServiceInfoProvider serviceInfoProvider;
    @Autowired
    private MqttClientCredentialsService credentialsService;

    private final List<MqttClient> clients = new ArrayList<>();
    private String serviceId;
    private MqttClientCredentials applicationCredentials;

    // Not named beforeTest: that would override the base method that resets the MQTT auth providers.
    @Before
    public void loginAndResolveNode() throws Exception {
        serviceId = serviceInfoProvider.getServiceId();
        loginSysAdmin();
    }

    @After
    public void afterTest() throws Exception {
        for (MqttClient client : clients) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
        if (applicationCredentials != null) {
            credentialsService.deleteCredentials(applicationCredentials.getId());
            mqttAuthProviderManagerService.disableAuthProvider(getMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC).getId());
        }
        MqttClient cleaner = new MqttClient(SERVER_URI + mqttPort, PERSISTED_CLIENT);
        cleaner.connect(options(true, 0L));
        cleaner.disconnect();
        cleaner.close();
    }

    @Test
    public void givenConnectedAndPersistedSessions_whenGetStatsByNode_thenThisNodeCountsThem() throws Exception {
        JsonNode before = baselineNodeStats();

        connect(CONNECTED_CLIENT, options(true, 0L));
        connect(PERSISTED_CLIENT, options(false, 3600L)).disconnect();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonNode after = nodeStats();
            assertThat(delta(before, after, "totalCount")).isEqualTo(2);
            assertThat(delta(before, after, "connectedCount")).isEqualTo(1);
            assertThat(delta(before, after, "disconnectedCount")).isEqualTo(1);
            assertThat(delta(before, after, "deviceCount")).isEqualTo(2);
            assertThat(delta(before, after, "applicationCount")).isZero();
            assertThat(delta(before, after, "integrationCount")).isZero();
        });
    }

    @Test
    public void givenApplicationClient_whenGetStatsByNode_thenItIsCountedAsApplication() throws Exception {
        applicationCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(APPLICATION_CLIENT, null));
        enableBasicProvider();
        JsonNode before = baselineNodeStats();

        connect(APPLICATION_CLIENT, options(true, 0L));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonNode after = nodeStats();
            assertThat(delta(before, after, "applicationCount")).isEqualTo(1);
            assertThat(delta(before, after, "deviceCount")).isZero();
            assertThat(delta(before, after, "connectedCount")).isEqualTo(1);
        });
    }

    // The endpoint memoizes its result, so the baseline is read once the memo of an earlier test has expired.
    private JsonNode baselineNodeStats() throws Exception {
        Thread.sleep(ClientSessionPageInfosImpl.STATS_BY_NODE_TTL_MS + 100);
        return nodeStats();
    }

    private JsonNode nodeStats() throws Exception {
        Map<String, JsonNode> statsByNode = doGetTyped(BY_NODE_URL, new TypeReference<>() {
        });
        return statsByNode.get(serviceId);
    }

    private static long delta(JsonNode before, JsonNode after, String field) {
        long beforeValue = before == null ? 0 : before.get(field).asLong();
        return after == null ? -beforeValue : after.get(field).asLong() - beforeValue;
    }

    private MqttClient connect(String clientId, MqttConnectionOptions options) throws Exception {
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, clientId);
        clients.add(client);
        client.connect(options);
        return client;
    }

    private static MqttConnectionOptions options(boolean cleanStart, long sessionExpiryInterval) {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(cleanStart);
        options.setSessionExpiryInterval(sessionExpiryInterval);
        return options;
    }

}
