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
package org.thingsboard.mqtt.broker.integration.service.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.integration.api.AbstractIntegration;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.queue.util.IntegrationProtoConverter;

import java.util.Properties;
import java.util.UUID;

@Slf4j
public class KafkaIntegration extends AbstractIntegration {

    private static final int TIMEOUT_MS = 10_000;

    private KafkaIntegrationConfig config;
    private Producer<String, String> producer;

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
                                ctx.getCallback().onFailure(throwable);
                            } else {
                                if (topicNames.contains(kafkaConfig.getTopic())) {
                                    ctx.getCallback().onSuccess();
                                } else {
                                    ctx.getCallback().onFailure(new ThingsboardException("Configured topic is missing on the external brokers!", ThingsboardErrorCode.GENERAL));
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
        startProcessingIntegrationMessages();
    }

    private String constructClientId() {
        return config.getClientIdPrefix() + "-" + context.getLifecycleMsg().getIntegrationId() + "-" + context.getServiceId();
    }

    KafkaProducer<String, String> getKafkaProducer(Properties properties) {
        return new KafkaProducer<>(properties);
    }

    @Override
    public void destroy() {
        stopProcessingPersistedMessages();
    }

    @Override
    public void destroyAndClearData() {
        stopProcessingPersistedMessages();
        clearIntegrationMessages();
    }

    private void startProcessingIntegrationMessages() {
        context.startProcessingIntegrationMessages(this);
    }

    private void stopProcessingPersistedMessages() {
        if (this.producer != null) {
            try {
                this.producer.close();
            } catch (Exception e) {
                log.error("[{}][{}] Failed to close Kafka producer", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName(), e);
            }
        }

        if (lifecycleMsg == null) {
            log.debug("Integration was not initialized properly. Skip stopProcessingPersistedMessages");
            return;
        }
        context.stopProcessingPersistedMessages(lifecycleMsg.getIntegrationId().toString());
    }

    private void clearIntegrationMessages() {
        if (lifecycleMsg == null) {
            log.debug("Integration was not initialized properly. Skip clearIntegrationMessages");
            return;
        }
        context.clearIntegrationMessages(lifecycleMsg.getIntegrationId().toString());
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
            Headers headers = new RecordHeaders();
            config.getKafkaHeaders().forEach((k, v) -> headers.add(new RecordHeader(k, v.getBytes(config.getKafkaHeadersCharset()))));

            ProducerRecord<String, String> kvProducerRecord = new ProducerRecord<>(config.getTopic(), null, config.getKey(), constructBody(msg), headers);
            producer.send(kvProducerRecord, (metadata, e) -> {
                if (e == null) {
                    log.debug("processRecord success {}{}{}", metadata.topic(),
                            metadata.partition(), metadata.offset());
                    callback.onSuccess();
                } else {
                    log.warn("[{}][{}] processException", getId(), getName(), e);
                    callback.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process message: {}", getId(), getName(), msg, e);
            callback.onFailure(e);
        }
    }

    private String constructBody(PublishIntegrationMsgProto msg) {
        PublishMsgProto publishMsgProto = msg.getPublishMsgProto();

        ObjectNode request = JacksonUtil.newObjectNode();
        request.put("payload", publishMsgProto.getPayload().toByteArray());
        request.put("topicName", publishMsgProto.getTopicName());
        request.put("clientId", publishMsgProto.getClientId());
        request.put("eventType", "PUBLISH_MSG");
        request.put("qos", publishMsgProto.getQos());
        request.put("retain", publishMsgProto.getRetain());
        request.put("tbmqIeNode", context.getServiceId());
        request.put("tbmqNode", msg.getTbmqNode());
        request.put("ts", msg.getTimestamp());
        request.set("props", IntegrationProtoConverter.fromProto(publishMsgProto.getUserPropertiesList()));
        request.set("metadata", JacksonUtil.valueToTree(metadataTemplate.getKvMap()));

        return JacksonUtil.toString(request);
    }

    private UUID getId() {
        return context.getLifecycleMsg().getIntegrationId();
    }

    private String getName() {
        return context.getLifecycleMsg().getName();
    }

}
