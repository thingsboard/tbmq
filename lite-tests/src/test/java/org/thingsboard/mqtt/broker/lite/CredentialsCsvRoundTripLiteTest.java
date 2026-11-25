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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.util.CsvUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The MQTT basic credentials the broker exports are the golden file it imports: importing
 * {@code csv/credentials-export-golden.csv} (from the application's test-jar, where the cluster edition connects with
 * its passwords) and exporting again gives back the same bytes.
 */
public class CredentialsCsvRoundTripLiteTest extends AbstractControllerTest {

    private static final String GOLDEN_CSV = "csv/credentials-export-golden.csv";
    private static final String EXPORT_URL = "/api/mqtt/client/credentials/export";
    private static final String IMPORT_URL = "/api/mqtt/client/credentials/bulk_import";

    private byte[] golden;
    private List<List<String>> goldenRows;

    @Before
    public void importGolden() throws Exception {
        try (InputStream in = new ClassPathResource(GOLDEN_CSV).getInputStream()) {
            golden = in.readAllBytes();
        }
        String csv = new String(golden, StandardCharsets.UTF_8);
        goldenRows = CsvUtils.parseCsv(csv, ',');
        loginSysAdmin();
        deleteBasicCredentialsOtherThanTheSystemOne();

        JsonNode result = doPostWithResponse(IMPORT_URL, importRequest(csv, goldenRows.get(0)), JsonNode.class);
        assertThat(result.get("errorsList")).isEmpty();
        assertThat(result.get("created").asInt()).isEqualTo(goldenRows.size() - 1);
    }

    @After
    public void deleteGoldenCredentials() {
        deleteBasicCredentialsOtherThanTheSystemOne();
    }

    @Test
    public void givenGoldenCsvImported_whenExported_thenByteEqual() throws Exception {
        MvcResult started = doGet(EXPORT_URL + "?includePasswordHashes=true&authRulesDelimiter=;")
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(golden);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=mqtt-client-credentials.csv");
        assertThat(result.getResponse().getHeader("x-filename")).isEqualTo("mqtt-client-credentials.csv");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(result.getResponse().getContentType()).startsWith("text/csv").containsIgnoringCase("charset=UTF-8");
    }

    @Test
    public void givenDefaultParameters_whenExported_thenHashesAndSemicolonDelimiter() throws Exception {
        MvcResult started = doGet(EXPORT_URL).andExpect(request().asyncStarted()).andReturn();

        byte[] body = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(golden);
    }

    @Test
    public void givenHashesSwitchedOff_whenExported_thenHashColumnIsEmpty() throws Exception {
        MvcResult started = doGet(EXPORT_URL + "?includePasswordHashes=false").andExpect(request().asyncStarted()).andReturn();

        String body = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("$2");
        List<List<String>> rows = CsvUtils.parseCsv(body, ',');
        assertThat(rows).hasSize(goldenRows.size());
        assertThat(rows.stream().skip(1).map(row -> row.get(6))).containsOnly("");
    }

    @Test
    public void givenPatternWithTheDelimiter_whenExported_thenBadRequestNamingTheCredentials() throws Exception {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName("export-delimiter");
        credentials.setClientType(ClientType.DEVICE);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsId(ProtocolUtil.clientIdCredentialsId("export-delimiter"));
        credentials.setCredentialsValue(JacksonUtil.toString(BasicMqttCredentials.newInstance("export-delimiter", null, null, List.of("a;b"))));
        doPost("/api/mqtt/client/credentials", credentials).andExpect(status().isOk());

        doGet(EXPORT_URL + "?authRulesDelimiter=;").andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Credentials 'export-delimiter' have an auth rule pattern containing ';'")));
        doGet(EXPORT_URL + "?authRulesDelimiter=ab").andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("authRulesDelimiter")));
    }

    @Test
    public void exportIsUnauthorizedWithoutALogin() throws Exception {
        logout();

        doGet(EXPORT_URL).andExpect(status().isUnauthorized());
    }

    private void deleteBasicCredentialsOtherThanTheSystemOne() {
        mqttClientCredentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC).stream()
                .filter(c -> !BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(c.getName()))
                .forEach(c -> mqttClientCredentialsService.deleteCredentials(c.getId()));
    }

    private static BulkImportRequest importRequest(String csv, List<String> header) {
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

}
