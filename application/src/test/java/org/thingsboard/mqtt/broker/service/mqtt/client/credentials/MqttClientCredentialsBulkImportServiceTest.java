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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportResult;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.entity.credentials.TbMqttClientCredentialsService;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttClientCredentialsBulkImportServiceTest {

    private static final String HASH = new BCryptPasswordEncoder(4).encode("pw-1");

    @Mock
    private MqttClientCredentialsService credentialsService;
    @Mock
    private TbMqttClientCredentialsService tbMqttClientCredentialsService;

    private BCryptPasswordEncoder passwordEncoder;
    private MqttClientCredentialsBulkImportService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        service = new MqttClientCredentialsBulkImportService(credentialsService, passwordEncoder, tbMqttClientCredentialsService);
        ReflectionTestUtils.invokeMethod(service, "initExecutor");
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.invokeMethod(service, "shutdownExecutor");
    }

    @Test
    void aHashIsStoredAsIs() throws Exception {
        when(tbMqttClientCredentialsService.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        BulkImportResult<MqttClientCredentials> result = importCsv("hashed," + HASH + "\n", BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrorsList()).isEmpty();
        assertThat(result.getCreated().get()).isEqualTo(1);
        ArgumentCaptor<MqttClientCredentials> saved = ArgumentCaptor.forClass(MqttClientCredentials.class);
        verify(tbMqttClientCredentialsService).save(saved.capture(), any());
        BasicMqttCredentials basic = JacksonUtil.fromString(saved.getValue().getCredentialsValue(), BasicMqttCredentials.class);
        assertThat(basic.getPassword()).isEqualTo(HASH);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void anEmptyPasswordCellNextToAHashIsNotBothColumns() throws Exception {
        when(tbMqttClientCredentialsService.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        BulkImportResult<MqttClientCredentials> result = importCsv("hashed,," + HASH + "\n",
                BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrorsList()).isEmpty();
        assertThat(result.getCreated().get()).isEqualTo(1);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void bothColumnsAreARowError() throws Exception {
        BulkImportResult<MqttClientCredentials> result = importCsv("both,plain," + HASH + "\n",
                BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrors().get()).isEqualTo(1);
        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['both'] A row may set PASSWORD or PASSWORD_HASH, not both");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    @Test
    void aBadHashIsARowError() throws Exception {
        BulkImportResult<MqttClientCredentials> result = importCsv("bad,not-a-hash\n", BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrors().get()).isEqualTo(1);
        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['bad'] PASSWORD_HASH must be a BCrypt hash with a cost from 4 to 14");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    @Test
    void aHashOnCredentialsWithAPasswordIsARowError() throws Exception {
        MqttClientCredentials existing = new MqttClientCredentials();
        existing.setId(UUID.randomUUID());
        existing.setName("existing");
        existing.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        BasicMqttCredentials basic = new BasicMqttCredentials();
        basic.setPassword(HASH);
        existing.setCredentialsValue(JacksonUtil.toString(basic));
        when(credentialsService.findCredentialsByName("existing")).thenReturn(existing);

        BulkImportResult<MqttClientCredentials> result = importCsv("existing," + HASH + "\n", BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrors().get()).isEqualTo(1);
        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['existing'] It is not allowed to change the credentials' password");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    @Test
    void theExcludedMarkerKeepsThePasswordOfExistingCredentialsAndAppliesTheRestOfTheRow() throws Exception {
        MqttClientCredentials existing = existing("existing", HASH);
        when(credentialsService.findCredentialsByName("existing")).thenReturn(existing);
        when(tbMqttClientCredentialsService.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        BulkImportResult<MqttClientCredentials> result = importCsv("existing,new-id," + BcryptHashFormat.EXCLUDED + "\n",
                BulkImportColumnType.NAME, BulkImportColumnType.CLIENT_ID, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrorsList()).isEmpty();
        assertThat(result.getUpdated().get()).isEqualTo(1);
        ArgumentCaptor<MqttClientCredentials> saved = ArgumentCaptor.forClass(MqttClientCredentials.class);
        verify(tbMqttClientCredentialsService).save(saved.capture(), any());
        BasicMqttCredentials basic = JacksonUtil.fromString(saved.getValue().getCredentialsValue(), BasicMqttCredentials.class);
        assertThat(basic.getPassword()).isEqualTo(HASH);
        assertThat(basic.getClientId()).isEqualTo("new-id");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void theExcludedMarkerIsARowErrorForNewCredentials() throws Exception {
        BulkImportResult<MqttClientCredentials> result = importCsv("fresh," + BcryptHashFormat.EXCLUDED + "\n",
                BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrors().get()).isEqualTo(1);
        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['fresh'] "
                + "The password hash was left out of the export: set PASSWORD or PASSWORD_HASH for these credentials");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    @Test
    void theExcludedMarkerIsARowErrorForExistingCredentialsWithoutAPassword() throws Exception {
        when(credentialsService.findCredentialsByName("open")).thenReturn(existing("open", null));

        BulkImportResult<MqttClientCredentials> result = importCsv("open," + BcryptHashFormat.EXCLUDED + "\n",
                BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrors().get()).isEqualTo(1);
        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['open'] "
                + "The password hash was left out of the export: set PASSWORD or PASSWORD_HASH for these credentials");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    @Test
    void theExcludedMarkerNextToAPasswordIsNotBothColumns() throws Exception {
        BulkImportResult<MqttClientCredentials> result = importCsv("both,plain," + BcryptHashFormat.EXCLUDED + "\n",
                BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['both'] A row may set PASSWORD or PASSWORD_HASH, not both");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    @Test
    void anotherNonBcryptValueIsStillABadHash() throws Exception {
        BulkImportResult<MqttClientCredentials> result = importCsv("bad,excluded\n", BulkImportColumnType.NAME, BulkImportColumnType.PASSWORD_HASH);

        assertThat(result.getErrorsList()).containsExactly("Line 1: IllegalArgumentException: ['bad'] PASSWORD_HASH must be a BCrypt hash with a cost from 4 to 14");
        verify(tbMqttClientCredentialsService, never()).save(any(), any());
    }

    private static MqttClientCredentials existing(String name, String password) {
        MqttClientCredentials existing = new MqttClientCredentials();
        existing.setId(UUID.randomUUID());
        existing.setName(name);
        existing.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        BasicMqttCredentials basic = new BasicMqttCredentials();
        basic.setPassword(password);
        existing.setCredentialsValue(JacksonUtil.toString(basic));
        return existing;
    }

    private BulkImportResult<MqttClientCredentials> importCsv(String csv, BulkImportColumnType... columns) throws Exception {
        BulkImportRequest.Mapping mapping = new BulkImportRequest.Mapping();
        mapping.setColumns(Arrays.stream(columns).map(type -> {
            BulkImportRequest.ColumnMapping column = new BulkImportRequest.ColumnMapping();
            column.setType(type);
            return column;
        }).toList());
        mapping.setDelimiter(',');
        mapping.setAuthRulesDelimiter(',');
        mapping.setHeader(false);
        BulkImportRequest request = new BulkImportRequest();
        request.setFile(csv);
        request.setMapping(mapping);
        return service.processBulkImport(request, new User());
    }

}
