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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponseHandler;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.topictree.TopicResolveService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeFixtures;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSnapshotBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicTreeControllerTest {

    @Mock
    TopicTreeService topicTreeService;
    @Mock
    TopicResolveService topicResolveService;
    @Mock
    IntegrationService integrationService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(topicTreeService.isEnabled()).thenReturn(true);
        when(topicTreeService.getSnapshot()).thenReturn(TopicTreeFixtures.fixture());
        TopicTreeController controller = new TopicTreeController(topicTreeService, topicResolveService);
        ReflectionTestUtils.setField(controller, "integrationService", integrationService);
        ReflectionTestUtils.setField(controller, "errorResponseHandler", new ThingsboardErrorResponseHandler());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(mock(SecurityUser.class), null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void childrenOmitUnrequestedColumnsAndClampPageSize() throws Exception {
        JsonNode page = json(mockMvc.perform(get("/api/topic-tree/children").param("pageSize", "1000")).andExpect(status().isOk()));
        assertThat(page.get("data")).hasSize(4);
        assertThat(page.get("totalElements").asInt()).isEqualTo(4);
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        assertThat(page.get("data").get(0).get("path").asText()).isEqualTo("sensors");
        assertThat(page.get("data").get(0).has("clientsTotal")).isFalse();

        mockMvc.perform(get("/api/topic-tree/children").param("columns", "clients,online").param("pageSize", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].clientsTotal").value(3))
                .andExpect(jsonPath("$.data[0].clientsOnline").value(2));
    }

    @Test
    void childrenOfPathAndSorting() throws Exception {
        assertThat(paths(get("/api/topic-tree/children").param("path", "sensors").param("sortProperty", "topic").param("sortOrder", "ASC")))
                .containsExactly("sensors/#", "sensors/+", "sensors/room1", "sensors/room2");
        mockMvc.perform(get("/api/topic-tree/children").param("path", "no/such"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Topic path not found: no/such"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/children", "/search?textSearch=temp", "/node", "/entities?path=a&kind=CLIENTS",
            "/subscriptions?path=a", "/retained?path=a", "/resolve?topic=a"})
    void disabledIs404(String uri) throws Exception {
        when(topicTreeService.isEnabled()).thenReturn(false);
        mockMvc.perform(get("/api/topic-tree" + uri)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Topic tree is disabled"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sortProperty=clients", "sortProperty=segment", "sortProperty=topic&sortOrder=SIDEWAYS", "columns=nope"})
    void childrenRejectUnknownSortAndColumns(String query) throws Exception {
        mockMvc.perform(get("/api/topic-tree/children?" + query)).andExpect(status().isBadRequest());
    }

    @Test
    void searchRejectsEmptyCriteria() throws Exception {
        for (String empty : List.of("?textSearch=", "?topic=", "?matchesTopic=", "?has=", "")) {
            mockMvc.perform(get("/api/topic-tree/search" + empty)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("At least one filter criterion is required"));
        }
        assertThat(paths(get("/api/topic-tree/search").param("textSearch", "RS/RO")))
                .contains("sensors/room1", "sensors/room1/#", "sensors/room2/temperature").doesNotContain("sensors");
    }

    @Test
    void searchTopicPrefixAndContainsModes() throws Exception {
        assertThat(paths(get("/api/topic-tree/search").param("textSearch", "a"))).contains("a", "sensors/room1/temperature");
        assertThat(paths(get("/api/topic-tree/search").param("topic", "a").param("topicMatch", "prefix")))
                .containsExactlyInAnyOrder("a", "a/", "a//b");
        assertThat(paths(get("/api/topic-tree/search").param("topic", "ROOM").param("topicMatch", "CONTAINS")))
                .containsExactlyInAnyOrder("sensors/room1", "sensors/room1/#", "sensors/room1/temperature",
                        "sensors/room2", "sensors/room2/temperature");
    }

    @ParameterizedTest
    @ValueSource(strings = {"topic=ab&topicMatch=PREFX", "has=FOO", "has=SUBSCRIPTIONS,FOO", "subscriptions=1&subscriptionOperation=BAD",
            "textSearch=ab&sortProperty=clients", "textSearch=ab&columns=weird"})
    void searchRejectsUnknownEnumValues(String query) throws Exception {
        mockMvc.perform(get("/api/topic-tree/search?" + query)).andExpect(status().isBadRequest());
    }

    @Test
    void searchThreadsFiltersAndSort() throws Exception {
        assertThat(paths(get("/api/topic-tree/search").param("has", "SUBSCRIPTIONS").param("subscriptions", "3")
                .param("subscriptionOperation", "greater_or_equal").param("sortProperty", "topic").param("sortOrder", "ASC")))
                .containsExactly("sensors", "sensors/+", "sensors/+/temperature");
        assertThat(paths(get("/api/topic-tree/search").param("has", "SUBSCRIPTIONS").param("has", "RETAINED")))
                .containsExactlyInAnyOrder("sensors", "sensors/room1");
        assertThat(paths(get("/api/topic-tree/search").param("wildcardOnly", "true").param("subscriptions", "1")))
                .containsExactlyInAnyOrder("sensors/#", "sensors/room1/#");
    }

    @Test
    void searchMatchesTopicResolvesFiltersAndRejectsInvalidTopics() throws Exception {
        when(topicResolveService.resolve("sensors/room1/temperature")).thenReturn(new TopicResolveDto("sensors/room1/temperature", 5, 3, List.of(
                new TopicResolveDto.Match("sensors/+/temperature", 3, 3, Map.of(), 0, 1),
                new TopicResolveDto.Match("sensors/room1/#", 1, 1, Map.of(), 0, 0),
                new TopicResolveDto.Match("sensors/#", 1, 0, Map.of(), 1, 0))));
        when(topicResolveService.resolve("a/#")).thenThrow(new DataValidationException("Topic name cannot contain wildcard characters!"));

        assertThat(paths(get("/api/topic-tree/search").param("matchesTopic", "sensors/room1/temperature")))
                .containsExactlyInAnyOrder("sensors/+/temperature", "sensors/room1/#", "sensors/#");
        assertThat(paths(get("/api/topic-tree/search").param("matchesTopic", "sensors/room1/temperature").param("has", "INTEGRATIONS")))
                .containsExactly("sensors/#");
        mockMvc.perform(get("/api/topic-tree/search").param("matchesTopic", "a/#")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Topic name cannot contain wildcard characters!"));
    }

    @Test
    void nodeReturnsRequestedColumnsIncludingSharedSubscriptions() throws Exception {
        mockMvc.perform(get("/api/topic-tree/node").param("path", "sensors").param("columns", "shared,online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("sensors"))
                .andExpect(jsonPath("$.subscriptionsTotal").value(5))
                .andExpect(jsonPath("$.sharedGroupsTotal").value(1))
                .andExpect(jsonPath("$.sharedSubscriptionsTotal").value(2))
                .andExpect(jsonPath("$.clientsOnline").value(2))
                .andExpect(jsonPath("$.integrationsTotal").doesNotExist());
        mockMvc.perform(get("/api/topic-tree/node"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.subscriptionsTotal").value(7));
        mockMvc.perform(get("/api/topic-tree/node").param("path", "gone")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/entities?kind=CLIENTS", "/subscriptions", "/retained"})
    void listEndpointsRequireAPath(String uri) throws Exception {
        mockMvc.perform(get("/api/topic-tree" + uri)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Topic path is required"));
    }

    @Test
    void entitiesRejectUnknownKindAndListClients() throws Exception {
        mockMvc.perform(get("/api/topic-tree/entities").param("path", "sensors").param("kind", "users"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/topic-tree/entities").param("path", "sensors").param("kind", "clients").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value("c1"))
                .andExpect(jsonPath("$.data[0].connected").value(true))
                .andExpect(jsonPath("$.data[0].clientType").value("DEVICE"));
        verify(integrationService, never()).findIntegrationById(any());
    }

    @Test
    void integrationNamesAreLookedUpOncePerDistinctIdAndNullForStaleOrInvalidIds() throws Exception {
        UUID known = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        Map<String, Set<TopicSubscription>> subs = Map.of(
                known.toString(), Set.of(new IntegrationTopicSubscription("t/a"), new IntegrationTopicSubscription("t/b")),
                stale.toString(), Set.of(new IntegrationTopicSubscription("t/c")),
                "not-a-uuid", Set.of(new IntegrationTopicSubscription("t/d")));
        when(topicTreeService.getSnapshot()).thenReturn(TopicTreeSnapshotBuilder.build(subs, List.of(), Map.of(), 0));
        Integration kafka = new Integration(known);
        kafka.setName("Kafka");
        when(integrationService.findIntegrationById(known)).thenReturn(kafka);

        JsonNode entities = json(mockMvc.perform(get("/api/topic-tree/entities").param("path", "t").param("kind", "INTEGRATIONS"))
                .andExpect(status().isOk()));
        Map<String, String> names = new java.util.HashMap<>();
        entities.get("data").forEach(e -> names.put(e.get("id").asText(), e.get("name").isNull() ? null : e.get("name").asText()));
        assertThat(names).containsEntry(known.toString(), "Kafka").containsEntry(stale.toString(), null).containsEntry("not-a-uuid", null);
        verify(integrationService, times(1)).findIntegrationById(known);
        verify(integrationService, times(1)).findIntegrationById(stale);

        JsonNode subscriptions = json(mockMvc.perform(get("/api/topic-tree/subscriptions").param("path", "t")).andExpect(status().isOk()));
        assertThat(subscriptions.get("totalElements").asInt()).isEqualTo(4);
        List<String> kafkaRows = new ArrayList<>();
        subscriptions.get("data").forEach(s -> {
            if (known.toString().equals(s.get("clientId").asText())) {
                kafkaRows.add(s.get("integrationName").asText());
            }
        });
        assertThat(kafkaRows).containsExactly("Kafka", "Kafka");
        verify(integrationService, times(2)).findIntegrationById(known);
    }

    @Test
    void retainedDelegatesWithClampedPaging() throws Exception {
        when(topicTreeService.getRetainedMessages(eq("a"), any())).thenReturn(new PageData<>());

        mockMvc.perform(get("/api/topic-tree/retained").param("path", "a").param("pageSize", "1000").param("page", "1")).andExpect(status().isOk());

        ArgumentCaptor<PageLink> retained = ArgumentCaptor.forClass(PageLink.class);
        verify(topicTreeService).getRetainedMessages(eq("a"), retained.capture());
        assertThat(retained.getValue().getPageSize()).isEqualTo(TopicTreeController.MAX_PAGE_SIZE);
        assertThat(retained.getValue().getPage()).isEqualTo(1);
        verify(topicTreeService, never()).getSnapshot();
    }

    @Test
    void resolveDelegatesAndRejectsInvalidTopics() throws Exception {
        when(topicResolveService.resolve("s/r1/t")).thenReturn(new TopicResolveDto("s/r1/t", 2, 1, List.of()));
        when(topicResolveService.resolve("s/#")).thenThrow(new DataValidationException("Topic name cannot contain wildcard characters!"));

        mockMvc.perform(get("/api/topic-tree/resolve").param("topic", "s/r1/t")).andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("s/r1/t")).andExpect(jsonPath("$.subscribersTotal").value(2));
        mockMvc.perform(get("/api/topic-tree/resolve").param("topic", "s/#")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Topic name cannot contain wildcard characters!"));
    }

    private List<String> paths(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        List<String> paths = new ArrayList<>();
        json(mockMvc.perform(request).andExpect(status().isOk())).get("data").forEach(n -> paths.add(n.get("path").asText()));
        return paths;
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return JacksonUtil.toJsonNode(result.andReturn().getResponse().getContentAsString());
    }
}
