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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    // ── CLIENT_AUTHENTICATED ──────────────────────────────────────────────────

    @Test
    void givenAuthenticatedSuccessProto_whenBuildBody_thenHasResultReasonAnonymous() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_AUTHENTICATED")
                .setClientId("c1")
                .setUsername("demo")
                .setResult("SUCCESS")
                .setReason("")
                .setAnonymous(false)
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("demo", body.get("username").asText());
        assertEquals("SUCCESS", body.get("result").asText());
        assertEquals("", body.get("reason").asText());
        assertFalse(body.get("anonymous").asBoolean());
        assertNull(body.get("authMethod"));
    }

    @Test
    void givenAuthenticatedFailureProto_whenBuildBody_thenHasFailureFields() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_AUTHENTICATED")
                .setClientId("c2")
                .setUsername("baduser")
                .setResult("FAILURE")
                .setReason("Bad credentials")
                .setAnonymous(false)
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("FAILURE", body.get("result").asText());
        assertEquals("Bad credentials", body.get("reason").asText());
        assertNull(body.get("authMethod"));
    }

    // ── CLIENT_AUTHORIZED ────────────────────────────────────────────────────

    @Test
    void givenAuthorizedDenyProto_whenBuildBody_thenHasActionResultTopicAndUsername() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_AUTHORIZED")
                .setClientId("c1")
                .setUsername("demo")
                .setAction("publish")
                .setResult("DENY")
                .setTopic("zxc/demo/topic")
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("demo", body.get("username").asText());
        assertEquals("publish", body.get("action").asText());
        assertEquals("DENY", body.get("result").asText());
        assertEquals("zxc/demo/topic", body.get("topic").asText());
    }

    @Test
    void givenAuthorizedAllowProto_whenBuildBody_thenHasActionResultTopic() {
        ClientLifecycleEventMsgProto msg = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_AUTHORIZED")
                .setClientId("c3")
                .setUsername("pub")
                .setAction("subscribe")
                .setResult("ALLOW")
                .setTopic("sensors/#")
                .build();
        ObjectNode body = integration.body(msg);
        assertEquals("subscribe", body.get("action").asText());
        assertEquals("ALLOW", body.get("result").asText());
        assertEquals("sensors/#", body.get("topic").asText());
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
