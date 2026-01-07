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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttClientCredentialsBulkImportService {

    private final MqttClientCredentialsService credentialsService;

    public BulkImportResult<MqttClientCredentials> processBulkImport(BulkImportRequest request) throws Exception {
        List<List<String>> records = CsvUtils.parseCsv(request.getFile(), request.getMapping().getDelimiter());
        BulkImportResult<MqttClientCredentials> result = new BulkImportResult<>();

        if (request.getMapping().getHeader() && !records.isEmpty()) {
            records.remove(0);
        }

        for (int i = 0; i < records.size(); i++) {
            try {
                List<String> record = records.get(i);
                MqttClientCredentials credentials = new MqttClientCredentials();
                BasicMqttCredentials basicCredentials = new BasicMqttCredentials();
                List<String> pubAuthRulePatterns = new ArrayList<>();
                List<String> subAuthRulePatterns = new ArrayList<>();
                credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
                for (int j = 0; j < request.getMapping().getColumns().size(); j++) {
                    if (j >= record.size()) break;
                    var columnMapping = request.getMapping().getColumns().get(j);
                    String value = record.get(j);
                    if (value == null || value.isEmpty()) continue;
                    setCredentialsField(credentials, basicCredentials, columnMapping.getType(), value,
                                      pubAuthRulePatterns, subAuthRulePatterns);
                }
                buildCredentialsValue(credentials, basicCredentials, pubAuthRulePatterns, subAuthRulePatterns);

                MqttClientCredentials savedCredentials = saveOrUpdate(credentials, request.getMapping().getUpdate());

                if (savedCredentials.getId() != null && request.getMapping().getUpdate()) {
                    result.getUpdated().incrementAndGet();
                } else {
                    result.getCreated().incrementAndGet();
                }
                result.getCreated().incrementAndGet();
            } catch (Exception e) {
                log.error("Error importing MQTT credentials at line {}", i + 1, e);
                result.getErrors().incrementAndGet();
                result.getErrorsList().add(String.format("Line %d: %s", i + 1, e.getMessage()));
            }
        }

        return result;
    }

    private void setCredentialsField(MqttClientCredentials entity, BasicMqttCredentials basicCredentials, 
                                   BulkImportColumnType type, String value,
                                   List<String> pubAuthRulePatterns, List<String> subAuthRulePatterns) {
        ObjectNode additionalInfo = getOrCreateAdditionalInfoObj(entity);
        switch (type) {
            case NAME:
                entity.setName(value);
                break;
            case CLIENT_TYPE:
                entity.setClientType(ClientType.valueOf(value.toUpperCase()));
                break;
            case CLIENT_ID:
                basicCredentials.setClientId(value);
                break;
            case USERNAME:
                basicCredentials.setUserName(value);
                break;
            case PASSWORD:
                basicCredentials.setPassword(value);
                break;
            case SUB_AUTH_RULE_PATTERNS:
                subAuthRulePatterns.addAll(parsePatterns(value));
                break;
            case PUB_AUTH_RULE_PATTERNS:
                pubAuthRulePatterns.addAll(parsePatterns(value));
                break;
            case DESCRIPTION:
                additionalInfo.set("description", new TextNode(value));
                break;
        }
        entity.setAdditionalInfo(additionalInfo);
    }

    private List<String> parsePatterns(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;|]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private void buildCredentialsValue(MqttClientCredentials entity, BasicMqttCredentials basicCredentials,
                                     List<String> pubAuthRulePatterns, List<String> subAuthRulePatterns) {
        PubSubAuthorizationRules authRules = new PubSubAuthorizationRules();
        authRules.setPubAuthRulePatterns(pubAuthRulePatterns.isEmpty() ? List.of(".*") : pubAuthRulePatterns);
        authRules.setSubAuthRulePatterns(subAuthRulePatterns.isEmpty() ? List.of(".*") : subAuthRulePatterns);
        basicCredentials.setAuthRules(authRules);
        entity.setCredentialsValue(JacksonUtil.toString(basicCredentials));
    }

    private MqttClientCredentials saveOrUpdate(MqttClientCredentials credentials, boolean update) {
        Optional<MqttClientCredentials> existing = Optional.ofNullable(credentialsService.findCredentialsByName(credentials.getName()));
        if (existing.isPresent()) {
            if (update) {
                MqttClientCredentials toUpdate = existing.get();
                toUpdate.setCredentialsId(credentials.getCredentialsId());
                toUpdate.setClientType(credentials.getClientType());
                toUpdate.setCredentialsType(credentials.getCredentialsType());
                toUpdate.setCredentialsValue(credentials.getCredentialsValue());
                return credentialsService.saveCredentials(toUpdate);
            }
            return existing.get();
        } else {
            return credentialsService.saveCredentials(credentials);
        }
    }

    private ObjectNode getOrCreateAdditionalInfoObj(HasAdditionalInfo entity) {
        return entity.getAdditionalInfo() == null || entity.getAdditionalInfo().isNull() ?
                JacksonUtil.newObjectNode() : (ObjectNode) entity.getAdditionalInfo();
    }
}