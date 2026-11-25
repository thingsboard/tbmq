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

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportResult;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.util.CsvUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports {@code csv/credentials-export-golden.csv}, the file the Lite round-trip test exports byte for byte, and
 * connects with the imported credentials. CSV has no comments, so the passwords are listed here: the password of
 * every row is {@code pw-<NAME>}, e.g. {@code pw-golden-01-plain}, and its PASSWORD_HASH is a BCrypt hash of it (cost 4
 * or 10, prefix {@code $2a$}, {@code $2b$} or {@code $2y$}).
 */
public class CredentialsCsvGoldenImportControllerTest extends AbstractControllerTest {

    static final String GOLDEN_CSV = "csv/credentials-export-golden.csv";
    private static final int NAME = 0;
    private static final int CLIENT_ID = 3;
    private static final int USERNAME = 4;
    private static final int PASSWORD_HASH = 6;

    private final List<String> importedNames = new ArrayList<>();

    @Before
    public void login() throws Exception {
        loginSysAdmin();
    }

    @After
    public void deleteImportedCredentials() {
        importedNames.stream().map(mqttClientCredentialsService::findCredentialsByName).filter(c -> c != null)
                .forEach(c -> mqttClientCredentialsService.deleteCredentials(c.getId()));
    }

    @Test
    public void givenGoldenCsv_whenImported_thenEachClientConnectsWithItsPassword() throws Exception {
        List<List<String>> credentials = importGolden();

        MqttAuthProvider basicProvider = getMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC);
        boolean wasEnabled = basicProvider.isEnabled();
        enableBasicProvider();
        try {
            for (List<String> row : credentials) {
                String name = row.get(NAME);
                MqttClientCredentials stored = mqttClientCredentialsService.findCredentialsByName(name);
                assertThat(stored).as(name).isNotNull();
                assertThat(stored.getCredentialsValue()).as(name).contains(row.get(PASSWORD_HASH));

                connectAndDisconnect(row, "pw-" + name);
                assertThatThrownBy(() -> connectAndDisconnect(row, "wrong-" + name)).as(name).isInstanceOf(MqttException.class);
            }
        } finally {
            if (!wasEnabled) {
                mqttAuthProviderManagerService.disableAuthProvider(basicProvider.getId());
            }
        }
    }

    @Test
    public void givenGoldenCsv_whenImportedAndExported_thenEveryGoldenLineIsInTheExport() throws Exception {
        String golden = readGolden();
        importGolden();

        MvcResult started = doGet("/api/mqtt/client/credentials/export").andExpect(request().asyncStarted()).andReturn();
        MockHttpServletResponse response = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=mqtt-client-credentials.csv");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("text/csv").containsIgnoringCase("charset=UTF-8");
        // credentials of other tests may be in the export too, so the lines are looked up, not compared as a whole
        List<String> exportedLines = List.of(response.getContentAsString(StandardCharsets.UTF_8).split("\r\n"));
        List<String> goldenLines = List.of(golden.split("\r\n"));
        assertThat(goldenLines).hasSizeGreaterThan(1);
        assertThat(exportedLines).containsAll(goldenLines);
    }

    private List<List<String>> importGolden() throws Exception {
        String golden = readGolden();
        List<List<String>> rows = CsvUtils.parseCsv(golden, ',');
        List<List<String>> credentials = rows.subList(1, rows.size());
        credentials.forEach(row -> importedNames.add(row.get(NAME)));

        BulkImportResult<?> result = JacksonUtil.fromString(
                doPost("/api/mqtt/client/credentials/bulk_import", importRequest(golden, rows.get(0)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                BulkImportResult.class);
        assertThat(result.getErrorsList()).isEmpty();
        assertThat(result.getCreated().get()).isEqualTo(credentials.size());
        return credentials;
    }

    static String readGolden() throws IOException {
        try (InputStream in = new ClassPathResource(GOLDEN_CSV).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static BulkImportRequest importRequest(String csv, List<String> header) {
        BulkImportRequest.Mapping mapping = new BulkImportRequest.Mapping();
        mapping.setColumns(header.stream().map(BulkImportColumnType::valueOf).map(type -> {
            BulkImportRequest.ColumnMapping column = new BulkImportRequest.ColumnMapping();
            column.setType(type);
            return column;
        }).toList());
        mapping.setDelimiter(',');
        mapping.setAuthRulesDelimiter(';');
        mapping.setHeader(true);
        BulkImportRequest request = new BulkImportRequest();
        request.setFile(csv);
        request.setMapping(mapping);
        return request;
    }

    private void connectAndDisconnect(List<String> row, String password) throws MqttException {
        String clientId = row.get(CLIENT_ID).isEmpty() ? "golden-connect-" + row.get(NAME).hashCode() : row.get(CLIENT_ID);
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, clientId, new MemoryPersistence());
        try {
            MqttConnectionOptions options = new MqttConnectionOptions();
            if (!row.get(USERNAME).isEmpty()) {
                options.setUserName(row.get(USERNAME));
            }
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
            options.setAutomaticReconnect(false);
            client.connect(options);
            assertThat(client.isConnected()).isTrue();
            client.disconnect();
        } finally {
            client.close(true);
        }
    }

}
