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
package org.thingsboard.mqtt.broker.service.mqtt.client.credentials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportResult;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.entity.credentials.TbMqttClientCredentialsService;
import org.thingsboard.mqtt.broker.util.CsvUtils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttClientCredentialsCsvExportServiceTest {

    private static final String HEADER = "NAME,DESCRIPTION,CLIENT_TYPE,CLIENT_ID,USERNAME,PASSWORD,PASSWORD_HASH,SUB_AUTH_RULE_PATTERNS,PUB_AUTH_RULE_PATTERNS";
    private static final String HASH = new BCryptPasswordEncoder(4).encode("pw-1");

    @Mock
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentialsCsvExportService service;

    @BeforeEach
    void setUp() {
        service = new MqttClientCredentialsCsvExportService(credentialsService);
    }

    @Test
    void writesTheHeaderAndOneRowPerBasicCredentialSortedByName() throws Exception {
        MqttClientCredentials b = credentials("b", "second", "b-id", "b-user", HASH, List.of("b/#"), List.of("b/+", "b/x"));
        MqttClientCredentials a = credentials("a", null, null, "a-user", null, List.of("a/#"), List.of("a/#"));
        a.setClientType(ClientType.APPLICATION);
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(b, a));

        String csv = export(true, ';');

        assertThat(csv).isEqualTo(HEADER + "\r\n"
                + "a,,APPLICATION,,a-user,,,a/#,a/#\r\n"
                + "b,second,DEVICE,b-id,b-user,," + HASH + ",b/#,b/+;b/x\r\n");
    }

    @Test
    void skipsTheSystemWebSocketCredentialAndNonBasicTypes() throws Exception {
        MqttClientCredentials system = credentials(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME, null, null,
                BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_USERNAME, null, List.of(".*"), List.of(".*"));
        MqttClientCredentials kept = credentials("kept", null, "kept-id", null, null, List.of(".*"), List.of(".*"));
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(system, kept));

        List<List<String>> rows = CsvUtils.parseCsv(export(true, ';'), ',');

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get(0)).isEqualTo("kept");
        // only MQTT_BASIC credentials are asked for
        verify(credentialsService).findByCredentialsType(ClientCredentialsType.MQTT_BASIC);
        verifyNoMoreInteractions(credentialsService);
    }

    @Test
    void escapesCommasAndQuotesAndKeepsNonAscii() throws Exception {
        MqttClientCredentials credentials = credentials("a,b", "say \"hi\", Ünïcødé — 日本語", "id,1", "us\"er", null, List.of("a/#"), List.of("b/#"));
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(credentials));

        List<List<String>> rows = CsvUtils.parseCsv(export(true, ';'), ',');

        assertThat(rows.get(1)).containsExactly("a,b", "say \"hi\", Ünïcødé — 日本語", "DEVICE", "id,1", "us\"er", "", "", "a/#", "b/#");
    }

    @Test
    void flattensDescriptionLineBreaks() throws Exception {
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(
                credentials("a", "one\r\ntwo\nthree\rfour\n\nfive", "a-id", null, null, List.of(".*"), List.of(".*"))));

        String csv = export(true, ';');

        assertThat(csv.split("\r\n", -1)).hasSize(3);
        assertThat(CsvUtils.parseCsv(csv, ',').get(1).get(1)).isEqualTo("one two three four  five");
    }

    @Test
    void refusesNamesWithLineBreaksAndPaddedPatterns() {
        MqttClientCredentials ok = credentials("ok", null, "ok-id", null, null, List.of(".*"), List.of(".*"));
        MqttClientCredentials lineBreak = credentials("a\nb", null, "a-id", null, null, List.of(".*"), List.of(".*"));
        MqttClientCredentials carriageReturn = credentials("c\rd", null, "c-id", null, null, List.of(".*"), List.of(".*"));
        MqttClientCredentials leading = credentials("lead", null, "l-id", null, null, List.of(" a/#"), List.of(".*"));
        MqttClientCredentials trailing = credentials("trail", null, "t-id", null, null, List.of(".*"), List.of("a/# "));

        assertRefused(ok, lineBreak, "Credentials 'a\nb' have a line break in the name");
        assertRefused(ok, carriageReturn, "Credentials 'c\rd' have a line break in the name");
        assertRefused(ok, leading, "Credentials 'lead' have an auth rule pattern with leading or trailing spaces, which the import removes");
        assertRefused(ok, trailing, "Credentials 'trail' have an auth rule pattern with leading or trailing spaces, which the import removes");
    }

    @Test
    void refusesAnEmptyPattern() {
        MqttClientCredentials ok = credentials("ok", null, "ok-id", null, null, List.of(".*"), List.of(".*"));

        assertRefused(ok, credentials("empty-sub", null, "e-id", null, null, List.of("a/#", ""), List.of(".*")),
                "Credentials 'empty-sub' have an empty auth rule pattern, which the import drops");
        assertRefused(ok, credentials("empty-pub", null, "e-id", null, null, List.of(".*"), List.of("")),
                "Credentials 'empty-pub' have an empty auth rule pattern, which the import drops");
    }

    @Test
    void refusesLineBreaksInTheClientIdTheUsernameAndPatterns() {
        MqttClientCredentials ok = credentials("ok", null, "ok-id", null, null, List.of(".*"), List.of(".*"));

        assertRefused(ok, credentials("id-lf", null, "a\nb", null, null, List.of(".*"), List.of(".*")),
                "Credentials 'id-lf' have a line break in the client id");
        assertRefused(ok, credentials("id-cr", null, "a\rb", null, null, List.of(".*"), List.of(".*")),
                "Credentials 'id-cr' have a line break in the client id");
        assertRefused(ok, credentials("user-lf", null, null, "a\nb", null, List.of(".*"), List.of(".*")),
                "Credentials 'user-lf' have a line break in the username");
        assertRefused(ok, credentials("user-cr", null, null, "a\rb", null, List.of(".*"), List.of(".*")),
                "Credentials 'user-cr' have a line break in the username");
        assertRefused(ok, credentials("sub-lf", null, "s-id", null, null, List.of("a\nb"), List.of(".*")),
                "Credentials 'sub-lf' have a line break in an auth rule pattern");
        assertRefused(ok, credentials("pub-cr", null, "p-id", null, null, List.of(".*"), List.of("a\rb")),
                "Credentials 'pub-cr' have a line break in an auth rule pattern");
    }

    @Test
    void leavesTheHashOutWhenSwitchedOff() throws Exception {
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(
                credentials("a", null, "a-id", null, HASH, List.of(".*"), List.of(".*"))));

        List<String> row = CsvUtils.parseCsv(export(false, ';'), ',').get(1);

        assertThat(row.get(5)).isEmpty();
        assertThat(row.get(6)).isEmpty();
        assertThat(export(false, ';')).doesNotContain(HASH);
    }

    @Test
    void emptyRuleListsAndAMissingPasswordExportAsEmptyCells() throws Exception {
        MqttClientCredentials noRules = credentials("none", null, "none-id", null, null, null, List.of());
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(noRules));

        String csv = export(true, ';');

        assertThat(CsvUtils.parseCsv(csv, ',').get(1)).containsExactly("none", "", "DEVICE", "none-id", "", "", "", "", "");

        // the import reads the cells back as empty rule lists and no password
        List<MqttClientCredentials> saved = new ArrayList<>();
        MqttClientCredentialsBulkImportService importService = importServiceSavingInto(saved);
        try {
            BulkImportResult<MqttClientCredentials> result = importService.processBulkImport(importRequest(csv), new User());
            assertThat(result.getErrorsList()).isEmpty();
        } finally {
            ReflectionTestUtils.invokeMethod(importService, "shutdownExecutor");
        }
        BasicMqttCredentials basic = JacksonUtil.fromString(saved.get(0).getCredentialsValue(), BasicMqttCredentials.class);
        assertThat(basic.getPassword()).isNull();
        assertThat(basic.getAuthRules().getSubAuthRulePatterns()).isNullOrEmpty();
        assertThat(basic.getAuthRules().getPubAuthRulePatterns()).isNullOrEmpty();
    }

    @Test
    void refusesAPatternContainingTheDelimiter() throws Exception {
        MqttClientCredentials credentials = credentials("semi", null, "semi-id", null, null, List.of("a;b"), List.of(".*"));
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(credentials));

        assertThatThrownBy(() -> export(true, ';')).isInstanceOf(DataValidationException.class)
                .hasMessage("Credentials 'semi' have an auth rule pattern containing ';': export with another auth rules delimiter");

        List<List<String>> rows = CsvUtils.parseCsv(export(true, '|'), ',');
        assertThat(rows.get(1).get(7)).isEqualTo("a;b");
    }

    private void assertRefused(MqttClientCredentials ok, MqttClientCredentials bad, String message) {
        when(credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC)).thenReturn(List.of(ok, bad));
        StringWriter out = new StringWriter();

        assertThatThrownBy(() -> service.export(true, ';', out)).isInstanceOf(DataValidationException.class).hasMessage(message);
        assertThat(out.toString()).isEmpty();
    }

    private String export(boolean includePasswordHashes, char delimiter) throws Exception {
        StringWriter out = new StringWriter();
        service.export(includePasswordHashes, delimiter, out);
        return out.toString();
    }

    private MqttClientCredentialsBulkImportService importServiceSavingInto(List<MqttClientCredentials> saved) {
        TbMqttClientCredentialsService tbService = mock(TbMqttClientCredentialsService.class);
        when(tbService.save(any(), any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        MqttClientCredentialsBulkImportService importService = new MqttClientCredentialsBulkImportService(
                mock(MqttClientCredentialsService.class), new BCryptPasswordEncoder(4), tbService);
        ReflectionTestUtils.invokeMethod(importService, "initExecutor");
        return importService;
    }

    private BulkImportRequest importRequest(String csv) {
        BulkImportRequest.Mapping mapping = new BulkImportRequest.Mapping();
        mapping.setColumns(Arrays.stream(csv.substring(0, csv.indexOf("\r\n")).split(","))
                .map(BulkImportColumnType::valueOf)
                .map(type -> {
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

    private static MqttClientCredentials credentials(String name, String description, String clientId, String username,
                                                     String password, List<String> subPatterns, List<String> pubPatterns) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName(name);
        credentials.setClientType(ClientType.DEVICE);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsValue(JacksonUtil.toString(
                new BasicMqttCredentials(clientId, username, password, new PubSubAuthorizationRules(pubPatterns, subPatterns))));
        if (description != null) {
            credentials.setAdditionalInfo(JacksonUtil.newObjectNode().put("description", description));
        }
        return credentials;
    }

}
