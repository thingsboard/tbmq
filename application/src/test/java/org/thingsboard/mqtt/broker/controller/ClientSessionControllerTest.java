/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
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
