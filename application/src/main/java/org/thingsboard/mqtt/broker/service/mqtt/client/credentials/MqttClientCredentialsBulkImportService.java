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

package org.thingsboard.mqtt.broker.service.mqtt.client.credentials;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.HasAdditionalInfo;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.sync.ie.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.sync.ie.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.sync.ie.importing.csv.BulkImportResult;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.util.CsvUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttClientCredentialsBulkImportService {

    private final MqttClientCredentialsService credentialsService;
    private final BCryptPasswordEncoder passwordEncoder;

    public BulkImportResult<MqttClientCredentials> processBulkImport(BulkImportRequest request) throws Exception {
        List<List<String>> records = CsvUtils.parseCsv(request.getFile(), request.getMapping().getDelimiter());
        BulkImportResult<MqttClientCredentials> result = new BulkImportResult<>();
        if (request.getMapping().getHeader() && !records.isEmpty()) {
            records.remove(0);
        }
        Character authRulesDelimiter = request.getMapping().getAuthRulesDelimiter();
        for (int i = 0; i < records.size(); i++) {
            int lineNumber = i + (request.getMapping().getHeader() ? 2 : 1);
            try {
                List<String> record = records.get(i);
                String name = getValueByColumnType(request, record, BulkImportColumnType.NAME);
                if (!StringUtils.hasText(name)) {
                    throw new IllegalArgumentException("Credential name is missing in CSV");
                }
                MqttClientCredentials existing = credentialsService.findCredentialsByName(name);
                MqttClientCredentials credentials;
                BasicMqttCredentials basicCredentials;
                boolean isUpdate = false;
                if (existing != null) {
                    credentials = existing;
                    basicCredentials = JacksonUtil.fromString(credentials.getCredentialsValue(), BasicMqttCredentials.class);
                    if (basicCredentials == null) basicCredentials = new BasicMqttCredentials();
                    isUpdate = true;
                } else {
                    credentials = new MqttClientCredentials();
                    credentials.setName(name);
                    basicCredentials = new BasicMqttCredentials();
                }
                credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
                for (int j = 0; j < request.getMapping().getColumns().size(); j++) {
                    if (j >= record.size()) break;
                    var columnMapping = request.getMapping().getColumns().get(j);
                    String value = record.get(j);
                    overwriteCredentialsField(credentials, basicCredentials, columnMapping.getType(), value, authRulesDelimiter);
                }
                credentials.setCredentialsValue(JacksonUtil.toString(basicCredentials));
                credentialsService.saveCredentials(credentials);
                if (isUpdate) {
                    result.getUpdated().incrementAndGet();
                } else {
                    result.getCreated().incrementAndGet();
                }
            } catch (Exception e) {
                log.error("Error importing MQTT credentials at line {}", lineNumber, e);
                result.getErrors().incrementAndGet();
                result.getErrorsList().add(String.format("Line %d: %s", lineNumber, e.getMessage()));
            }
        }
        return result;
    }

    private void overwriteCredentialsField(MqttClientCredentials entity, BasicMqttCredentials basic,
                                           BulkImportColumnType type, String value, Character delimiter) {
        ObjectNode additionalInfo = getOrCreateAdditionalInfoObj(entity);
        String processedValue = StringUtils.hasText(value) ? value.trim() : null;
        switch (type) {
            case CLIENT_TYPE -> entity.setClientType(processedValue != null ? ClientType.valueOf(processedValue.toUpperCase()) : null);
            case CLIENT_ID -> basic.setClientId(processedValue);
            case USERNAME -> basic.setUserName(processedValue);
            case PASSWORD -> basic.setPassword(processedValue != null ? passwordEncoder.encode(processedValue) : null);
            case SUB_AUTH_RULE_PATTERNS -> overwriteAuthRules(basic, processedValue, true, delimiter);
            case PUB_AUTH_RULE_PATTERNS -> overwriteAuthRules(basic, processedValue, false, delimiter);
            case DESCRIPTION -> {
                if (processedValue != null) {
                    additionalInfo.set("description", new TextNode(processedValue));
                } else {
                    additionalInfo.remove("description");
                }
            }
        }
        entity.setAdditionalInfo(additionalInfo);
    }

    private void overwriteAuthRules(BasicMqttCredentials basic, String value, boolean isSub, Character delimiter) {
        PubSubAuthorizationRules authRules = basic.getAuthRules();
        if (authRules == null) {
            authRules = new PubSubAuthorizationRules();
            basic.setAuthRules(authRules);
        }
        if (value == null) {
            if (isSub) {
                authRules.setSubAuthRulePatterns(null);
            } else {
                authRules.setPubAuthRulePatterns(null);
            }
        } else {
            List<String> patterns = parsePatterns(value, delimiter);
            if (isSub) {
                authRules.setSubAuthRulePatterns(patterns);
            } else {
                authRules.setPubAuthRulePatterns(patterns);
            }
        }
    }

    private List<String> parsePatterns(String value, Character delimiter) {
        if (!StringUtils.hasText(value)) return List.of();
        String delimiterRegex = Pattern.quote(String.valueOf(delimiter));
        return Arrays.stream(value.split(delimiterRegex))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private String getValueByColumnType(BulkImportRequest request, List<String> record, BulkImportColumnType type) {
        for (int j = 0; j < request.getMapping().getColumns().size(); j++) {
            if (request.getMapping().getColumns().get(j).getType() == type && j < record.size()) {
                return record.get(j);
            }
        }
        return null;
    }

    private ObjectNode getOrCreateAdditionalInfoObj(HasAdditionalInfo entity) {
        return entity.getAdditionalInfo() == null || entity.getAdditionalInfo().isNull() ?
                JacksonUtil.newObjectNode() : (ObjectNode) entity.getAdditionalInfo();
    }
}