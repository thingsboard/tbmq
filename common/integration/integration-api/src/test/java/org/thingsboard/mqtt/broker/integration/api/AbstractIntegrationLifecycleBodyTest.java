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
package org.thingsboard.mqtt.broker.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.RetainHandling;
import org.thingsboard.mqtt.broker.gen.queue.SubscriptionOptionsProto;
import org.thingsboard.mqtt.broker.gen.queue.TopicSubscriptionProto;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractIntegrationLifecycleBodyTest {

    /**
     * Minimal concrete subclass — only implements the one method the compiler requires
     * (process, which AbstractIntegration leaves unimplemented). metadataTemplate is set
     * directly before each test so constructLifecycleEventBody doesn't NPE.
     */
    static class TestIntegration extends AbstractIntegration {
        @Override
        public void process(PublishIntegrationMsgProto msg, IntegrationMsgCallback callback) {
            // no-op — not exercised by these tests
        }

        ObjectNode body(ClientLifecycleEventMsgProto msg) {
            return constructLifecycleEventBody(msg);
        }
    }

    private TestIntegration integration;

    @BeforeEach
    void setUp() {
        integration = new TestIntegration();
        // Set an empty metadata template so constructLifecycleEventBody doesn't NPE.
        integration.metadataTemplate = new UplinkMetaData(ContentType.JSON, Map.of());
    }

    // ── COMMON: username present in every event type ──────────────────────────

    @Test
    void givenConnectedProto_whenBuildBody_thenUsernamePresent() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_CONNECTED")
                .setClientId("c1")
                .setUsername("alice")
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("alice", body.get("username").asText());
    }

    @Test
    void givenNoUsernameOrIp_whenBuildBody_thenEmptyStringKeysOmitted() {
        // auth disabled => no username; address-less session => no ipAddress
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_CONNECTED")
                .setClientId("c1")
                .build();
        ObjectNode body = integration.body(msg);
        assertFalse(body.has("username"));
        assertFalse(body.has("ipAddress"));
        // the discriminator and populated fields stay present
        assertEquals("CLIENT_CONNECTED", body.get("eventType").asText());
        assertEquals("c1", body.get("clientId").asText());
    }

    // ── CLIENT_CONNECTED: protocolVersion + sessionExpiryInterval ────────────

    @Test
    void givenConnectedProto_whenBuildBody_thenHasProtocolVersionAndSessionExpiryInterval() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_CONNECTED")
                .setClientId("c1")
                .setUsername("alice")
                .setProtocolVersion(5)
                .setSessionExpiryInterval(3600L)
                .setCleanStart(true)
                .setKeepAlive(60)
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals(5, body.get("protocolVersion").asInt());
        assertEquals(3600L, body.get("sessionExpiryInterval").asLong());
        // existing fields must still be present
        assertEquals(true, body.get("cleanStart").asBoolean());
        assertEquals(60, body.get("keepAlive").asInt());
    }

    // ── CLIENT_AUTHENTICATION_FAILED ──────────────────────────────────────────

    @Test
    void givenAuthenticationFailedProto_whenBuildBody_thenHasReason() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_AUTHENTICATION_FAILED")
                .setClientId("c2")
                .setUsername("baduser")
                .setReason("Bad credentials")
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("baduser", body.get("username").asText());
        assertEquals("Bad credentials", body.get("reason").asText());
    }

    // ── CLIENT_AUTHORIZATION_FAILED ───────────────────────────────────────────

    @Test
    void givenAuthorizationFailedProto_whenBuildBody_thenHasActionTopicAndUsername() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_AUTHORIZATION_FAILED")
                .setClientId("c1")
                .setUsername("demo")
                .setAction("publish")
                .setTopic("zxc/demo/topic")
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("demo", body.get("username").asText());
        assertEquals("publish", body.get("action").asText());
        assertEquals("zxc/demo/topic", body.get("topic").asText());
    }

    // ── CLIENT_CONNECTION_FAILED ──────────────────────────────────────────────

    @Test
    void givenConnectionFailedProto_whenBuildBody_thenHasReason() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_CONNECTION_FAILED")
                .setClientId("c3")
                .setReason("QUOTA_EXCEEDED")
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("QUOTA_EXCEEDED", body.get("reason").asText());
    }

    // ── CLIENT_SUBSCRIBED: full subscription details ──────────────────────────

    @Test
    void givenSubscribedProto_whenBuildBody_thenHasFullSubscriptionDetails() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_SUBSCRIBED")
                .setClientId("c1")
                .addSubscriptions(TopicSubscriptionProto.newBuilder()
                        .setTopic("foo/bar")
                        .setQos(1)
                        .setShareName("g1")
                        .setSubscriptionId(7)
                        .setOptions(SubscriptionOptionsProto.newBuilder()
                                .setNoLocal(true)
                                .setRetainAsPublish(true)
                                .setRetainHandling(RetainHandling.DONT_SEND)
                                .build())
                        .build())
                .build();
        ObjectNode body = integration.body(msg);
        JsonNode sub = body.get("subscriptions").get(0);
        assertEquals("foo/bar", sub.get("topicFilter").asText());
        assertEquals(1, sub.get("qos").asInt());
        assertEquals("g1", sub.get("shareName").asText());
        assertEquals(7, sub.get("subscriptionId").asInt());
        assertTrue(sub.get("options").get("noLocal").asBoolean());
        assertTrue(sub.get("options").get("retainAsPublish").asBoolean());
        assertEquals("DONT_SEND", sub.get("options").get("retainHandling").asText());
    }

    @Test
    void givenSubscribedProtoWithoutShareOrSubId_whenBuildBody_thenOptionalKeysOmitted() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_SUBSCRIBED")
                .setClientId("c1")
                .addSubscriptions(TopicSubscriptionProto.newBuilder()
                        .setTopic("foo/bar")
                        .setQos(0)
                        .setOptions(SubscriptionOptionsProto.getDefaultInstance())
                        .build())
                .build();
        ObjectNode body = integration.body(msg);
        JsonNode sub = body.get("subscriptions").get(0);
        assertFalse(sub.has("shareName"));
        assertFalse(sub.has("subscriptionId"));
        assertEquals("SEND", sub.get("options").get("retainHandling").asText());
    }

    // ── metadata node always present ──────────────────────────────────────────

    @Test
    void givenAnyProto_whenBuildBody_thenMetadataNodePresent() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_DISCONNECTED")
                .setClientId("c1")
                .setUsername("u")
                .build();
        ObjectNode body = integration.body(msg);
        assertNotNull(body.get("metadata"));
    }
}
