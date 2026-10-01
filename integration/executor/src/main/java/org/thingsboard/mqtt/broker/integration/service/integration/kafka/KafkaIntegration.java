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
package org.thingsboard.mqtt.broker.integration.service.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UserPropertyProto;
import org.thingsboard.mqtt.broker.integration.api.AbstractIntegration;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.api.template.IntegrationTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Slf4j
public class KafkaIntegration extends AbstractIntegration {

    private static final int TIMEOUT_MS = 10_000;

    private KafkaIntegrationConfig config;
    private Producer<String, String> producer;
    private IntegrationTemplate keyTemplate;
    private List<HeaderTemplate> headerTemplates;
    private boolean templated;

    @Override
    public void doValidateConfiguration(JsonNode clientConfiguration, boolean allowLocalNetworkHosts) throws ThingsboardException {
        try {
            KafkaConfigValidator.validate(getClientConfiguration(clientConfiguration, KafkaIntegrationConfig.class));
        } catch (Exception e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void doCheckConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {
        try {
            KafkaIntegrationConfig kafkaConfig = getClientConfiguration(integration, KafkaIntegrationConfig.class);
            KafkaConfigValidator.validateBootstrapServers(kafkaConfig.getBootstrapServers());
            KafkaConfigValidator.validateTopic(kafkaConfig.getTopic());

            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
            props.putAll(kafkaConfig.getOtherProperties());

            try (Admin admin = Admin.create(props)) {
                admin.listTopics(new ListTopicsOptions().timeoutMs(TIMEOUT_MS))
                        .names()
                        .whenComplete((topicNames, throwable) -> {
                            if (throwable != null) {
                                ctx.getCheckConnectionCallback().onFailure(throwable);
                            } else {
                                if (topicNames.contains(kafkaConfig.getTopic())) {
                                    ctx.getCheckConnectionCallback().onSuccess();
                                } else {
                                    ctx.getCheckConnectionCallback().onFailure(new ThingsboardException("Configured topic is missing on the external brokers!", ThingsboardErrorCode.GENERAL));
                                }
                            }
                        });
            }
        } catch (Exception e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);

        config = getClientConfiguration(lifecycleMsg, KafkaIntegrationConfig.class);
        compileTemplates();
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, constructClientId());
        properties.put(ProducerConfig.RETRIES_CONFIG, config.getRetries());
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getBatchSize());
        properties.put(ProducerConfig.LINGER_MS_CONFIG, config.getLinger());
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, config.getBufferMemory());
        properties.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getCompression());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, config.getKeySerializer());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, config.getValueSerializer());
        config.getOtherProperties().forEach((k, v) -> {
            if (SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG.equals(k)
                    || SslConfigs.SSL_KEYSTORE_KEY_CONFIG.equals(k)
                    || SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG.equals(k)) {
                v = v.replace("\\n", "\n");
            }
            properties.put(k, v);
        });
        this.producer = getKafkaProducer(properties);
        startProcessingIntegrationMessages(this);
    }

    private void compileTemplates() {
        keyTemplate = StringUtils.isEmpty(config.getKey()) ? null : compile(KafkaConfigValidator.KEY_LABEL, config.getKey());
        headerTemplates = config.getKafkaHeaders().entrySet().stream()
                .map(header -> new HeaderTemplate(header.getKey(), compile(KafkaConfigValidator.headerLabel(header.getKey()), header.getValue())))
                .toList();
        templated = (keyTemplate != null && keyTemplate.hasPlaceholders())
                || headerTemplates.stream().anyMatch(header -> header.value().hasPlaceholders());
    }

    // Stored configs are not re-validated on upgrade: a value that fails the save-time check keeps its pre-template
    // behaviour (sent verbatim) instead of stopping the integration from starting.
    private IntegrationTemplate compile(String label, String value) {
        try {
            return IntegrationTemplate.parse(label, value);
        } catch (IllegalArgumentException e) {
            log.warn("[{}][{}] {}. Sending the value as literal text", getId(), getName(), e.getMessage());
            return IntegrationTemplate.literal(value);
        }
    }

    private String constructClientId() {
        return config.getClientIdPrefix() + "-" + context.getLifecycleMsg().getIntegrationId() + "-" + context.getServiceId();
    }

    Producer<String, String> getKafkaProducer(Properties properties) {
        return new KafkaProducer<>(properties);
    }

    @Override
    public void doStopClient() {
        if (this.producer != null) {
            try {
                this.producer.close();
            } catch (Exception e) {
                log.error("[{}][{}] Failed to close Kafka producer", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName(), e);
            }
        }
    }

    @Override
    public void process(PublishIntegrationMsgProto msg, IntegrationMsgCallback integrationMsgCallback) {
        context.getExternalCallExecutor().executeAsync(() -> {
            publish(msg, integrationMsgCallback);
            return null;
        });
    }

    private void publish(PublishIntegrationMsgProto msg, IntegrationMsgCallback callback) {
        try {
            ObjectNode body = config.isSendOnlyMsgPayload() ? (templated ? constructBody(msg, false) : null) : constructBody(msg);
            String value = config.isSendOnlyMsgPayload() ? getPayloadValue(msg) : JacksonUtil.toString(body);
            Headers headers = buildHeaders(body);
            if (config.isForwardUserProperties()) {
                for (UserPropertyProto property : msg.getPublishMsgProto().getUserPropertiesList()) {
                    headers.add(new RecordHeader(property.getKey(), property.getValue().getBytes(config.getKafkaHeadersCharset())));
                }
            }
            send(new ProducerRecord<>(config.getTopic(), null, resolveKey(body), value, headers), callback);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process message: {}", getId(), getName(), msg, e);
            handleMsgProcessingFailure(e);
            callback.onFailure(e);
        }
    }

    @Override
    protected void doProcessLifecycleEvent(ObjectNode body, IntegrationMsgCallback callback) {
        String value = JacksonUtil.toString(body);
        context.getExternalCallExecutor().executeAsync(() -> {
            publishBody(body, value, callback);
            return null;
        });
    }

    private void publishBody(ObjectNode body, String value, IntegrationMsgCallback callback) {
        try {
            send(new ProducerRecord<>(config.getTopic(), null, resolveKey(body), value, buildHeaders(body)), callback);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to publish lifecycle event body", getId(), getName(), e);
            handleMsgProcessingFailure(e);
            callback.onFailure(e);
        }
    }

    private void send(ProducerRecord<String, String> record, IntegrationMsgCallback callback) {
        producer.send(record, (metadata, e) -> {
            if (e == null) {
                log.debug("[{}][{}] processRecord success {}{}{}", getId(), getName(), metadata.topic(),
                        metadata.partition(), metadata.offset());
                integrationStatistics.incMessagesProcessed();
                callback.onSuccess();
            } else {
                log.warn("[{}][{}] processException", getId(), getName(), e);
                handleMsgProcessingFailure(e);
                callback.onFailure(e);
            }
        });
    }

    // body is null only when nothing is templated, and a static template returns its text without reading it
    private String resolveKey(ObjectNode body) {
        return keyTemplate == null ? null : keyTemplate.resolve(body).orElse(null);
    }

    // Built per record: the producer marks a record's Headers read-only on send, and interceptors may add to them.
    private Headers buildHeaders(ObjectNode body) {
        Headers headers = new RecordHeaders();
        for (HeaderTemplate header : headerTemplates) {
            header.value().resolve(body).ifPresent(value ->
                    headers.add(new RecordHeader(header.name(), value.getBytes(config.getKafkaHeadersCharset()))));
        }
        return headers;
    }

    private String getPayloadValue(PublishIntegrationMsgProto msg) {
        ByteString payload = msg.getPublishMsgProto().getPayload();
        return payload.isValidUtf8() ? payload.toStringUtf8() : Base64.getEncoder().encodeToString(payload.toByteArray());
    }

    private record HeaderTemplate(String name, IntegrationTemplate value) {
    }

}
