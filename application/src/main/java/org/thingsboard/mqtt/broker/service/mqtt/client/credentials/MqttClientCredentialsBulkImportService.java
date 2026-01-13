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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.HasAdditionalInfo;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportResult;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.util.CsvUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttClientCredentialsBulkImportService {

    private final MqttClientCredentialsService credentialsService;
    private final BCryptPasswordEncoder passwordEncoder;

    private ExecutorService executor;

    @PostConstruct
    private void initExecutor() {
        executor = ThingsBoardExecutors.newLimitedTasksExecutor(Runtime.getRuntime().availableProcessors(), 100_000, "bulk-import");
    }

    @PreDestroy
    private void shutdownExecutor() {
        if (executor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(executor, "Bulk import executor");
        }
    }

    public final BulkImportResult<MqttClientCredentials> processBulkImport(BulkImportRequest request) throws Exception {
        List<EntityData> entitiesData = parseData(request);

        BulkImportResult<MqttClientCredentials> result = new BulkImportResult<>();
        CountDownLatch completionLatch = new CountDownLatch(entitiesData.size());

        Character authRulesDelimiter = request.getMapping().getAuthRulesDelimiter();

        entitiesData.forEach(entityData -> DonAsynchron.submit(() -> saveEntity(entityData.getFields(), authRulesDelimiter),
                importedEntityInfo -> {
                    if (importedEntityInfo.isUpdated()) {
                        log.debug("Updated MQTT credentials [{}] at line {}", importedEntityInfo.getEntity(), entityData.getLineNumber());
                        result.getUpdated().incrementAndGet();
                    } else {
                        log.debug("Created MQTT credentials [{}] at line {}", importedEntityInfo.getEntity(), entityData.getLineNumber());
                        result.getCreated().incrementAndGet();
                    }
                    completionLatch.countDown();
                },
                throwable -> {
                    log.debug("Error importing MQTT credentials at line {}", entityData.getLineNumber(), throwable);
                    result.getErrors().incrementAndGet();
                    result.getErrorsList().add(String.format("Line %d: %s", entityData.getLineNumber(), ExceptionUtils.getRootCauseMessage(throwable)));
                    completionLatch.countDown();
                },
                executor));

        completionLatch.await();
        return result;
    }

    private List<EntityData> parseData(BulkImportRequest request) throws Exception {
        List<List<String>> records = CsvUtils.parseCsv(request.getFile(), request.getMapping().getDelimiter());
        AtomicInteger linesCounter = new AtomicInteger(0);

        if (request.getMapping().getHeader()) {
            records.remove(0);
            linesCounter.incrementAndGet();
        }

        List<BulkImportRequest.ColumnMapping> columnsMappings = request.getMapping().getColumns();
        return records.stream()
                .map(record -> {
                    EntityData entityData = new EntityData();
                    Stream.iterate(0, i -> i < record.size(), i -> i + 1)
                            .map(i -> Map.entry(columnsMappings.get(i), record.get(i)))
                            .filter(entry -> entry.getKey().getType().isAuthRules() || StringUtils.isNotEmpty(entry.getValue()))
                            .forEach(entry -> entityData.getFields().put(entry.getKey().getType(), entry.getValue()));
                    entityData.setLineNumber(linesCounter.incrementAndGet());
                    return entityData;
                })
                .collect(Collectors.toList());
    }

    private ImportedEntityInfo<MqttClientCredentials> saveEntity(Map<BulkImportColumnType, String> fields,
                                                                 Character authRulesDelimiter) {
        ImportedEntityInfo<MqttClientCredentials> importedEntityInfo = new ImportedEntityInfo<>();

        MqttClientCredentials entity = findOrCreateEntity(fields.get(BulkImportColumnType.NAME));
        BasicMqttCredentials basicCredentials;
        if (entity.getId() != null) {

            if (entity.getCredentialsType() != ClientCredentialsType.MQTT_BASIC) {
                throw new IllegalArgumentException(String.format("Credentials with name '%s' already exists and it's not MQTT Basic credentials", entity.getName()));
            }

            importedEntityInfo.setUpdated(true);

            basicCredentials = JacksonUtil.fromString(entity.getCredentialsValue(), BasicMqttCredentials.class);
            if (StringUtils.isNotEmpty(basicCredentials.getPassword()) && fields.containsKey(BulkImportColumnType.PASSWORD)) {
                throw new IllegalArgumentException(String.format("['%s'] It is not allowed to change the credentials' password", entity.getName()));
            }
        } else {
            entity.setCredentialsType(ClientCredentialsType.MQTT_BASIC);

            basicCredentials = new BasicMqttCredentials();
            basicCredentials.setAuthRules(PubSubAuthorizationRules.defaultInstance());
        }

        setEntityFields(entity, basicCredentials, fields, authRulesDelimiter);

        MqttClientCredentials savedEntity = saveEntity(entity);

        importedEntityInfo.setEntity(savedEntity);
        return importedEntityInfo;
    }

    private void setEntityFields(MqttClientCredentials entity,
                                 BasicMqttCredentials basic,
                                 Map<BulkImportColumnType, String> fields,
                                 Character delimiter) {
        ObjectNode additionalInfo = getOrCreateAdditionalInfoObj(entity);
        fields.forEach((columnType, value) -> {
            switch (columnType) {
                case NAME -> entity.setName(value);
                case CLIENT_TYPE -> entity.setClientType(ClientType.valueOf(value.toUpperCase()));
                case CLIENT_ID -> basic.setClientId(value);
                case USERNAME -> basic.setUserName(value);
                case PASSWORD -> basic.setPassword(passwordEncoder.encode(value));
                case SUB_AUTH_RULE_PATTERNS -> overwriteAuthRules(basic, value, true, delimiter);
                case PUB_AUTH_RULE_PATTERNS -> overwriteAuthRules(basic, value, false, delimiter);
                case DESCRIPTION -> additionalInfo.set("description", new TextNode(value));
            }
            entity.setAdditionalInfo(additionalInfo);
        });
        entity.setCredentialsValue(JacksonUtil.toString(basic));
    }

    private void overwriteAuthRules(BasicMqttCredentials basic, String value, boolean isSub, Character delimiter) {
        PubSubAuthorizationRules authRules = basic.getAuthRules();
        if (StringUtils.isEmpty(value)) {
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
        String delimiterRegex = Pattern.quote(String.valueOf(delimiter));
        return Arrays.stream(value.split(delimiterRegex))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private ObjectNode getOrCreateAdditionalInfoObj(HasAdditionalInfo entity) {
        return entity.getAdditionalInfo() == null || entity.getAdditionalInfo().isNull() ?
                JacksonUtil.newObjectNode() : (ObjectNode) entity.getAdditionalInfo();
    }

    private MqttClientCredentials findOrCreateEntity(String name) {
        MqttClientCredentials credentialsByName = credentialsService.findCredentialsByName(name);
        return credentialsByName == null ? new MqttClientCredentials() : credentialsByName;
    }

    private MqttClientCredentials saveEntity(MqttClientCredentials credentials) {
        return credentialsService.saveCredentials(credentials);
    }

    @Data
    protected static class EntityData {

        private final Map<BulkImportColumnType, String> fields = new LinkedHashMap<>();
        private int lineNumber;

    }
}
