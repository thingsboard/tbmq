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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UserPropertyProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaIntegrationTest {

    private MockProducer<String, String> producer;
    private IntegrationContext context;
    private IntegrationMsgCallback callback;

    @BeforeEach
    void setUp() {
        producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        context = mock(IntegrationContext.class);
        when(context.getExternalCallExecutor()).thenReturn(new DirectExecutor());
        when(context.getServiceId()).thenReturn("ie-1");
        callback = mock(IntegrationMsgCallback.class);
    }

    // ── messages ──────────────────────────────────────────────────────────────

    @Test
    void givenTemplatedKeyAndHeaders_whenPayloadOnly_thenRawValueResolvedKeyAndHeaders() throws Exception {
        ObjectNode config = baseConfig().put("sendOnlyMsgPayload", true).put("key", "${clientId}");
        config.putObject("kafkaHeaders").put("mqtt-topic", "${topicName}").put("username", "${username}");

        start(config).process(message(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(record.value()).isEqualTo("hello");
        assertThat(record.key()).isEqualTo("c1");
        assertThat(headerValues(record, "mqtt-topic")).containsExactly("sensors/1");
        assertThat(headerValues(record, "username")).containsExactly("alice");
        verify(callback).onSuccess();
    }

    @Test
    void givenTemplatedKey_whenJsonMode_thenKeyResolvedAndBodyHasUsernameAndPayload() throws Exception {
        start(baseConfig().put("key", "dev-${username}")).process(message(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(record.key()).isEqualTo("dev-alice");
        JsonNode value = JacksonUtil.toJsonNode(record.value());
        assertThat(value.get("username").asText()).isEqualTo("alice");
        assertThat(value.has("payload")).isTrue();
    }

    @Test
    void givenKeyPlaceholderWithoutValue_whenProcess_thenNullKeyAndDelivered() throws Exception {
        start(baseConfig().put("key", "${clientCertCn}")).process(message(), callback);

        assertThat(single().key()).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void givenMessageWithoutUsername_whenUsernameKey_thenNullKeyAndDelivered() throws Exception {
        PublishIntegrationMsgProto msg = message().toBuilder()
                .setPublishMsgProto(message().getPublishMsgProto().toBuilder().clearUsername())
                .build();

        start(baseConfig().put("key", "${username}")).process(msg, callback);

        assertThat(single().key()).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void givenHeaderPlaceholderWithoutValue_whenProcess_thenHeaderLeftOutAndOthersKept() throws Exception {
        ObjectNode config = baseConfig();
        config.putObject("kafkaHeaders").put("cn", "${clientCertCn}").put("mqtt-topic", "${topicName}").put("source", "tbmq");

        start(config).process(message(), callback);

        assertThat(headerNames(single())).containsExactly("mqtt-topic", "source");
    }

    @Test
    void givenForwardUserProperties_whenProcess_thenPropertiesInOrderWithDuplicates() throws Exception {
        start(baseConfig().put("forwardUserProperties", true)).process(message(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(headerNames(record)).containsExactly("site", "tag", "tag");
        assertThat(headerValues(record, "tag")).containsExactly("x", "y");
        assertThat(headerValues(record, "site")).containsExactly("A");
    }

    @Test
    void givenForwardUserPropertiesNamedLikeConfiguredHeader_whenProcess_thenOnlyConfiguredValueSent() throws Exception {
        ObjectNode config = baseConfig().put("forwardUserProperties", true);
        config.putObject("kafkaHeaders").put("tag", "configured");

        start(config).process(message(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(headerNames(record)).containsExactly("tag", "site");
        assertThat(headerValues(record, "tag")).containsExactly("configured");
    }

    @Test
    void givenConfiguredHeaderResolvesEmpty_whenPropertyHasSameName_thenNoHeaderSent() throws Exception {
        ObjectNode config = baseConfig().put("forwardUserProperties", true);
        config.putObject("kafkaHeaders").put("cn", "${clientCertCn}");
        PublishIntegrationMsgProto msg = message().toBuilder()
                .setPublishMsgProto(message().getPublishMsgProto().toBuilder().addUserProperties(property("cn", "forged")))
                .build();

        start(config).process(msg, callback);

        assertThat(headerValues(single(), "cn")).isEmpty();
    }

    @Test
    void givenForwardUserPropertiesOff_whenProcess_thenNoPropertyHeaders() throws Exception {
        start(baseConfig()).process(message(), callback);

        assertThat(headerNames(single())).isEmpty();
    }

    @Test
    void givenStaticKeyAndHeaders_whenPayloadOnly_thenSameRecordAsBefore() throws Exception {
        ObjectNode config = baseConfig().put("sendOnlyMsgPayload", true).put("key", "static-key");
        config.putObject("kafkaHeaders").put("source", "tbmq");

        start(config).process(message(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(record.key()).isEqualTo("static-key");
        assertThat(record.value()).isEqualTo("hello");
        assertThat(headerValues(record, "source")).containsExactly("tbmq");
    }

    @Test
    void givenStoredInvalidTemplate_whenInit_thenStartsAndSendsLiteral() throws Exception {
        start(baseConfig().put("key", "${unknown}")).process(message(), callback);

        assertThat(single().key()).isEqualTo("${unknown}");
        verify(callback).onSuccess();
    }

    @Test
    void givenStoredNullHeaderValue_whenInit_thenHeaderSentWithEmptyValue() throws Exception {
        ObjectNode config = baseConfig();
        config.putObject("kafkaHeaders").putNull("empty");

        start(config).process(message(), callback);

        assertThat(headerValues(single(), "empty")).containsExactly("");
        verify(callback).onSuccess();
    }

    @Test
    void givenBinaryPayloadAndTemplatedKey_whenPayloadOnly_thenBase64ValueAndResolvedKey() throws Exception {
        PublishIntegrationMsgProto msg = message().toBuilder()
                .setPublishMsgProto(message().getPublishMsgProto().toBuilder()
                        .setPayload(ByteString.copyFrom(new byte[]{(byte) 0xFF, 0x00})))
                .build();

        start(baseConfig().put("sendOnlyMsgPayload", true).put("key", "${clientId}")).process(msg, callback);

        ProducerRecord<String, String> record = single();
        assertThat(record.value()).isEqualTo("/wA=");
        assertThat(record.key()).isEqualTo("c1");
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void givenStoredInvalidTemplate_whenValidatedOnStartup_thenAccepted() {
        // the executor runs validateConfiguration before init() on every restart and re-enable
        IntegrationLifecycleMsg lifecycleMsg = lifecycleMsg(baseConfig().put("key", "${topic}"));

        assertThatCode(() -> new KafkaIntegration().validateConfiguration(lifecycleMsg, true)).doesNotThrowAnyException();
    }

    @Test
    void givenInvalidTemplate_whenValidatedOnSave_thenRejected() {
        IntegrationLifecycleMsg lifecycleMsg = lifecycleMsg(baseConfig().put("key", "${topic}"));

        assertThatThrownBy(() -> new KafkaIntegration().validateConfigurationOnSave(lifecycleMsg))
                .isInstanceOf(ThingsboardException.class)
                .hasMessageStartingWith("Key: unknown placeholder '${topic}'");
    }

    // ── lifecycle events ─────────────────────────────────────────────────────

    @Test
    void givenTemplatedKeyAndHeaders_whenLifecycleEvent_thenResolvedFromEventBody() throws Exception {
        ObjectNode config = baseConfig().put("key", "${clientId}");
        config.putObject("kafkaHeaders").put("user", "${username}").put("mqtt-topic", "${topicName}");

        start(config).processLifecycleEvent(event(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(record.key()).isEqualTo("c1");
        assertThat(headerNames(record)).containsExactly("user");
        assertThat(headerValues(record, "user")).containsExactly("alice");
        assertThat(JacksonUtil.toJsonNode(record.value()).get("eventType").asText()).isEqualTo("CLIENT_CONNECTED");
        verify(callback).onSuccess();
    }

    @Test
    void givenMessageOnlyKeyPlaceholder_whenLifecycleEvent_thenNullKeyAndDelivered() throws Exception {
        start(baseConfig().put("key", "${props.site}").put("forwardUserProperties", true))
                .processLifecycleEvent(event(), callback);

        ProducerRecord<String, String> record = single();
        assertThat(record.key()).isNull();
        assertThat(headerNames(record)).isEmpty();
        verify(callback).onSuccess();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ObjectNode baseConfig() {
        ObjectNode config = JacksonUtil.newObjectNode();
        config.put("bootstrapServers", "localhost:9092");
        config.put("topic", "tbmq.messages");
        config.put("acks", "all");
        config.put("compression", "none");
        return config;
    }

    private static IntegrationLifecycleMsg lifecycleMsg(ObjectNode clientConfiguration) {
        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.set("clientConfiguration", clientConfiguration);
        return IntegrationLifecycleMsg.builder()
                .integrationId(UUID.randomUUID())
                .name("kafka")
                .configuration(configuration)
                .build();
    }

    private KafkaIntegration start(ObjectNode clientConfiguration) throws Exception {
        IntegrationLifecycleMsg lifecycleMsg = lifecycleMsg(clientConfiguration);
        when(context.getLifecycleMsg()).thenReturn(lifecycleMsg);
        KafkaIntegration integration = new KafkaIntegration() {
            @Override
            Producer<String, String> getKafkaProducer(Properties properties) {
                return producer;
            }
        };
        integration.init(new TbIntegrationInitParams(context, lifecycleMsg));
        return integration;
    }

    private static PublishIntegrationMsgProto message() {
        return PublishIntegrationMsgProto.newBuilder()
                .setPublishMsgProto(PublishMsgProto.newBuilder()
                        .setClientId("c1")
                        .setUsername("alice")
                        .setTopicName("sensors/1")
                        .setQos(1)
                        .setPayload(ByteString.copyFromUtf8("hello"))
                        .addUserProperties(property("site", "A"))
                        .addUserProperties(property("tag", "x"))
                        .addUserProperties(property("tag", "y")))
                .setTbmqNode("tbmq-1")
                .setTimestamp(1000L)
                .build();
    }

    private static UserPropertyProto property(String key, String value) {
        return UserPropertyProto.newBuilder().setKey(key).setValue(value).build();
    }

    private static ClientLifecycleEventMsgProto event() {
        return ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_CONNECTED")
                .setClientId("c1")
                .setUsername("alice")
                .setTs(1000L)
                .build();
    }

    private ProducerRecord<String, String> single() {
        assertThat(producer.history()).hasSize(1);
        return producer.history().get(0);
    }

    private static List<String> headerNames(ProducerRecord<String, String> record) {
        return Arrays.stream(record.headers().toArray()).map(Header::key).toList();
    }

    private static List<String> headerValues(ProducerRecord<String, String> record, String name) {
        return StreamSupport.stream(record.headers().headers(name).spliterator(), false)
                .map(header -> new String(header.value(), StandardCharsets.UTF_8))
                .toList();
    }

    private static class DirectExecutor implements ListeningExecutor {
        @Override
        public <T> ListenableFuture<T> executeAsync(Callable<T> task) {
            try {
                return Futures.immediateFuture(task.call());
            } catch (Exception e) {
                return Futures.immediateFailedFuture(e);
            }
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
