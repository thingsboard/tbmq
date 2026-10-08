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
package org.thingsboard.mqtt.broker.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;

@DaoSqlTest
public class LookupEndpointsControllerTest extends AbstractControllerTest {

    private static final String LEGACY_SUFFIX = "Legacy";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void givenByKeyPaths_whenCalled_thenRoutedToDocumentedHandlers() throws Exception {
        doGet("/api/app/shared/subs/by-topic?topic={v}", "t").andExpect(handler().methodName("getSharedSubscriptionByTopic"));
        doGet("/api/client-session/by-client-id?clientId={v}", "c").andExpect(handler().methodName("getDetailedClientSessionInfo"));
        doGet("/api/mqtt/client/credentials/by-name?name={v}", "n").andExpect(handler().methodName("getClientCredentialsByName"));
        doGet("/api/retained-msg/by-topic?topicName={v}", "t").andExpect(handler().methodName("getRetainedMessage"));
        doGet("/api/subscription/by-client-id?clientId={v}", "c").andExpect(handler().methodName("getClientSubscriptions"));
        doGet("/api/unauthorized/client/by-client-id?clientId={v}", "c").andExpect(handler().methodName("getUnauthorizedClient"));
        doDelete("/api/unauthorized/client/by-client-id", "clientId", "c").andExpect(handler().methodName("deleteUnauthorizedClient"));
        doGet("/api/ws/connection/by-name?name={v}", "n").andExpect(handler().methodName("getWebSocketConnectionByName"));
    }

    @Test
    public void givenLegacyQueryOnlyPaths_whenCalled_thenStillRoutedToLegacyHandlers() throws Exception {
        doGet("/api/app/shared/subs?topic={v}", "t").andExpect(handler().methodName("getSharedSubscriptionByTopicLegacy"));
        doGet("/api/client-session?clientId={v}", "c").andExpect(handler().methodName("getDetailedClientSessionInfoLegacy"));
        doGet("/api/mqtt/client/credentials?name={v}", "n").andExpect(handler().methodName("getClientCredentialsByNameLegacy"));
        doGet("/api/retained-msg?topicName={v}", "t").andExpect(handler().methodName("getRetainedMessageLegacy"));
        doGet("/api/subscription?clientId={v}", "c").andExpect(handler().methodName("getClientSubscriptionsLegacy"));
        doGet("/api/unauthorized/client?clientId={v}", "c").andExpect(handler().methodName("getUnauthorizedClientLegacy"));
        doDelete("/api/unauthorized/client", "clientId", "c").andExpect(handler().methodName("deleteUnauthorizedClientLegacy"));
        doGet("/api/ws/connection?name={v}", "n").andExpect(handler().methodName("getWebSocketConnectionByNameLegacy"));
    }

    @Test
    public void givenPagedPaths_whenCalled_thenRoutingUnchanged() throws Exception {
        doGet("/api/app/shared/subs?pageSize=1&page=0").andExpect(handler().methodName("getSharedSubscriptions"));
        doGet("/api/client-session?pageSize=1&page=0").andExpect(handler().methodName("getShortClientSessionInfos"));
        doGet("/api/mqtt/client/credentials?pageSize=1&page=0").andExpect(handler().methodName("getCredentials"));
        doGet("/api/retained-msg?pageSize=1&page=0").andExpect(handler().methodName("getRetainedMessages"));
        doGet("/api/subscription?pageSize=1&page=0").andExpect(handler().methodName("getSharedSubscriptions"));
        doGet("/api/unauthorized/client?pageSize=1&page=0").andExpect(handler().methodName("getUnauthorizedClients"));
        doGet("/api/ws/connection?pageSize=1&page=0").andExpect(handler().methodName("getWebSocketConnections"));
    }

    @Test
    public void givenLegacyTwinWithDivergentAuthority_whenCheckParity_thenViolationReported() throws Exception {
        Method legacy = DivergentTwinController.class.getDeclaredMethod("lookupLegacy", String.class);
        assertThat(legacyTwinViolations(List.of(legacy))).hasSize(1);
    }

    @Test
    public void givenLegacyHandlers_thenHiddenAndSameAuthorityAsDocumentedTwin() {
        List<Method> legacyHandlers = handlerMapping.getHandlerMethods().values().stream()
                .map(HandlerMethod::getMethod)
                .filter(method -> method.getName().endsWith(LEGACY_SUFFIX))
                .distinct()
                .toList();
        assertThat(legacyHandlers).hasSizeGreaterThanOrEqualTo(8);
        assertThat(legacyTwinViolations(legacyHandlers)).isEmpty();
    }

    private static List<String> legacyTwinViolations(List<Method> legacyHandlers) {
        List<String> violations = new ArrayList<>();
        for (Method legacy : legacyHandlers) {
            String documentedName = legacy.getName().substring(0, legacy.getName().length() - LEGACY_SUFFIX.length());
            Method documented;
            try {
                documented = legacy.getDeclaringClass().getDeclaredMethod(documentedName, legacy.getParameterTypes());
            } catch (NoSuchMethodException e) {
                violations.add(legacy + " has no documented twin " + documentedName);
                continue;
            }
            if (!legacy.isAnnotationPresent(Hidden.class)) {
                violations.add(legacy + " must be @Hidden");
            }
            PreAuthorize legacyAuthority = legacy.getAnnotation(PreAuthorize.class);
            PreAuthorize documentedAuthority = documented.getAnnotation(PreAuthorize.class);
            if (legacyAuthority == null || documentedAuthority == null || !legacyAuthority.value().equals(documentedAuthority.value())) {
                violations.add(legacy + " must have the same @PreAuthorize as " + documented);
            }
        }
        return violations;
    }

    private static class DivergentTwinController {

        @PreAuthorize("hasAuthority('SYS_ADMIN')")
        public void lookup(String key) {
        }

        @Hidden
        @PreAuthorize("permitAll()")
        public void lookupLegacy(String key) {
        }
    }
}
