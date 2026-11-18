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

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;

/**
 * The application shared subscription endpoints of {@link LookupEndpointsControllerTest}: Lite has no application
 * shared subscriptions, so these are kept out of the suite that runs there.
 */
@DaoSqlTest
public class AppSharedSubscriptionLookupControllerTest extends AbstractControllerTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void givenByKeyPath_whenCalled_thenRoutedToDocumentedHandler() throws Exception {
        doGet("/api/app/shared/subs/by-topic?topic={v}", "t").andExpect(handler().methodName("getSharedSubscriptionByTopic"));
    }

    @Test
    public void givenLegacyQueryOnlyPath_whenCalled_thenStillRoutedToLegacyHandler() throws Exception {
        doGet("/api/app/shared/subs?topic={v}", "t").andExpect(handler().methodName("getSharedSubscriptionByTopicLegacy"));
    }

    @Test
    public void givenPagedPath_whenCalled_thenRoutingUnchanged() throws Exception {
        doGet("/api/app/shared/subs?pageSize=1&page=0").andExpect(handler().methodName("getSharedSubscriptions"));
    }

    @Test
    public void givenLegacyHandlers_thenGetSharedSubscriptionByTopicLegacyIsAmongThem() {
        assertThat(handlerMapping.getHandlerMethods().values().stream()
                .map(HandlerMethod::getMethod)
                .map(method -> method.getName())
                .distinct())
                .contains("getSharedSubscriptionByTopicLegacy");
    }

}
