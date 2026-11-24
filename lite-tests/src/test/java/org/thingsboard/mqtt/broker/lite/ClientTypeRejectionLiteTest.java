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
package org.thingsboard.mqtt.broker.lite;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.client.ClientTypeConstraint;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lite refuses the APPLICATION client type when credentials are saved, by the REST API and by the bulk import.
 */
public class ClientTypeRejectionLiteTest extends AbstractControllerTest {

    private static final String CREDENTIALS_URL = "/api/mqtt/client/credentials";
    private static final String PROVIDER_URL = "/api/mqtt/auth/provider";

    @Autowired
    private MqttClientCredentialsDao credentialsDao;

    private final List<String> names = new ArrayList<>();

    @Before
    public void login() throws Exception {
        loginSysAdmin();
    }

    @After
    public void deleteCredentials() {
        names.stream().map(mqttClientCredentialsService::findCredentialsByName).filter(c -> c != null)
                .forEach(c -> mqttClientCredentialsService.deleteCredentials(c.getId()));
    }

    @Test
    public void givenApplicationCredentials_whenSave_thenBadRequest() throws Exception {
        doPost(CREDENTIALS_URL, basicCredentials("reject-app", ClientType.APPLICATION))
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(ClientTypeConstraint.LITE_MESSAGE)));
        assertThat(mqttClientCredentialsService.findCredentialsByName("reject-app")).isNull();

        doPost(CREDENTIALS_URL, basicCredentials("reject-dev", ClientType.DEVICE)).andExpect(status().isOk());
        assertThat(mqttClientCredentialsService.findCredentialsByName("reject-dev")).isNotNull();
    }

    @Test
    public void givenStoredApplicationCredentials_whenRenamed_thenBadRequest_andWhenTypeChanged_thenSaved() throws Exception {
        // written through the DAO, as an older store would hold it
        MqttClientCredentials stored = credentialsDao.save(basicCredentials("stored-app", ClientType.APPLICATION));
        names.add("stored-app");
        names.add("stored-renamed");

        stored.setName("stored-renamed");
        doPost(CREDENTIALS_URL, stored)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(ClientTypeConstraint.LITE_MESSAGE)));
        assertThat(mqttClientCredentialsService.findCredentialsByName("stored-app")).isNotNull();

        stored.setClientType(ClientType.DEVICE);
        MqttClientCredentials saved = doPost(CREDENTIALS_URL, stored, MqttClientCredentials.class);
        assertThat(saved.getId()).isEqualTo(stored.getId());
        assertThat(saved.getClientType()).isEqualTo(ClientType.DEVICE);
        assertThat(saved.getName()).isEqualTo("stored-renamed");
    }

    @Test
    public void givenCsvWithOneApplicationRow_whenBulkImport_thenOthersImported() throws Exception {
        names.addAll(List.of("bulk-a", "bulk-b", "bulk-c"));
        BulkImportRequest request = new BulkImportRequest();
        request.setFile("name,type,clientId\nbulk-a,DEVICE,bulk-a\nbulk-b,APPLICATION,bulk-b\nbulk-c,DEVICE,bulk-c\n");
        BulkImportRequest.Mapping mapping = new BulkImportRequest.Mapping();
        mapping.setDelimiter(',');
        mapping.setAuthRulesDelimiter(';');
        mapping.setHeader(true);
        mapping.setColumns(List.of(column(BulkImportColumnType.NAME), column(BulkImportColumnType.CLIENT_TYPE), column(BulkImportColumnType.CLIENT_ID)));
        request.setMapping(mapping);

        JsonNode result = doPostWithResponse(CREDENTIALS_URL + "/bulk_import", request, JsonNode.class);

        assertThat(result.get("created").asInt()).isEqualTo(2);
        assertThat(result.get("updated").asInt()).isZero();
        assertThat(result.get("errors").asInt()).isEqualTo(1);
        assertThat(result.get("errorsList")).hasSize(1);
        assertThat(result.get("errorsList").get(0).asText()).startsWith("Line 3: ").endsWith(ClientTypeConstraint.LITE_MESSAGE);
        assertThat(mqttClientCredentialsService.findCredentialsByName("bulk-a")).isNotNull();
        assertThat(mqttClientCredentialsService.findCredentialsByName("bulk-b")).isNull();
        assertThat(mqttClientCredentialsService.findCredentialsByName("bulk-c")).isNotNull();
    }

    @Test
    public void givenJwtProviderWithApplication_whenSave_thenBadRequest() throws Exception {
        MqttAuthProvider provider = getMqttAuthProvider(MqttAuthProviderType.JWT);
        JwtMqttAuthProviderConfiguration configuration = (JwtMqttAuthProviderConfiguration) provider.getConfiguration();

        configuration.setDefaultClientType(ClientType.APPLICATION);
        doPost(PROVIDER_URL, provider).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(ClientTypeConstraint.LITE_MESSAGE)));

        configuration.setDefaultClientType(ClientType.DEVICE);
        configuration.setClientTypeClaims(Map.of("role", "app"));
        doPost(PROVIDER_URL, provider).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(ClientTypeConstraint.LITE_MESSAGE)));

        configuration.setClientTypeClaims(null);
        doPost(PROVIDER_URL, provider).andExpect(status().isOk());
    }

    @Test
    public void givenHttpProviderWithApplicationDefault_whenSave_thenBadRequest() throws Exception {
        MqttAuthProvider provider = getMqttAuthProvider(MqttAuthProviderType.HTTP);
        HttpMqttAuthProviderConfiguration configuration = (HttpMqttAuthProviderConfiguration) provider.getConfiguration();

        configuration.setDefaultClientType(ClientType.APPLICATION);
        doPost(PROVIDER_URL, provider).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(ClientTypeConstraint.LITE_MESSAGE)));

        configuration.setDefaultClientType(ClientType.DEVICE);
        doPost(PROVIDER_URL, provider).andExpect(status().isOk());
    }

    private static BulkImportRequest.ColumnMapping column(BulkImportColumnType type) {
        BulkImportRequest.ColumnMapping column = new BulkImportRequest.ColumnMapping();
        column.setType(type);
        return column;
    }

    private static MqttClientCredentials basicCredentials(String name, ClientType clientType) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName(name);
        credentials.setClientType(clientType);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsId(ProtocolUtil.clientIdCredentialsId(name));
        credentials.setCredentialsValue(JacksonUtil.toString(BasicMqttCredentials.newInstance(name, null, null, List.of(".*"))));
        return credentials;
    }

}
