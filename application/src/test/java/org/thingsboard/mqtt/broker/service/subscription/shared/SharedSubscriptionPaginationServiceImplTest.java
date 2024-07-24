/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionState;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SharedSubscriptionPaginationServiceImplTest {

    SharedSubscriptionCacheService sharedSubscriptionCacheService;
    ClientSessionCache clientSessionCache;
    SharedSubscriptionPaginationServiceImpl sharedSubscriptionPaginationService;

    @Before
    public void setUp() throws Exception {
        sharedSubscriptionCacheService = mock(SharedSubscriptionCacheService.class);
        clientSessionCache = mock(ClientSessionCache.class);
        sharedSubscriptionPaginationService = spy(new SharedSubscriptionPaginationServiceImpl(sharedSubscriptionCacheService, clientSessionCache));
    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(sharedSubscriptionCacheService, clientSessionCache);
    }

    @Test
    public void givenNoSharedSubscriptions_whenExecuteGetSharedSubscriptions_thenReturnNothing() {
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(Map.of());

        PageLink pageLink = new PageLink(100);
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertTrue(pageData.getData().isEmpty());
        assertEquals(0, pageData.getTotalPages());
        assertEquals(0, pageData.getTotalElements());
        assertFalse(pageData.hasNext());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithTextSearch_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("another/topic/filter", "g1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "g1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2"), "g1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c2")).thenReturn(getClientSessionInfo("c2", ClientType.APPLICATION, true));

        PageLink pageLink = new PageLink(100, 0, "another");
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertEquals(1, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(1, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("g1", sharedSubscriptionDto.getShareName());
        assertEquals("another/topic/filter", sharedSubscriptionDto.getTopicFilter());
        assertEquals(2, sharedSubscriptionDto.getClients().size());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithTextSearchAndWithNullClient_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("another/topic/filter", "g1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "g1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2"), "g1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c2")).thenReturn(null);

        PageLink pageLink = new PageLink(100, 0);
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertEquals(1, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(1, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("g1", sharedSubscriptionDto.getShareName());
        assertEquals("another/topic/filter", sharedSubscriptionDto.getTopicFilter());
        assertEquals(1, sharedSubscriptionDto.getClients().size());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithAbsentTextSearch_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0", ClientType.APPLICATION, true), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("another/topic/filter", "g1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1", ClientType.APPLICATION, true), "g1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2", ClientType.APPLICATION, true), "g1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);

        PageLink pageLink = new PageLink(100, 0, "notPresent");
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertEquals(0, pageData.getData().size());
        assertEquals(0, pageData.getTotalPages());
        assertEquals(0, pageData.getTotalElements());
        assertFalse(pageData.hasNext());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithSmallerPageSize_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("client"), "group"))
                ),
                new TopicSharedSubscription("another/topic/filter", "g1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "g1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2"), "g1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("client")).thenReturn(getClientSessionInfo("client", ClientType.DEVICE, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c2")).thenReturn(getClientSessionInfo("c2", ClientType.APPLICATION, true));

        PageLink pageLink = new PageLink(2, 0);
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertEquals(2, pageData.getData().size());
        assertEquals(2, pageData.getTotalPages());
        assertEquals(3, pageData.getTotalElements());
        assertTrue(pageData.hasNext());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithSortOrderByTopicFilter_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("client"), "group"))
                ),
                new TopicSharedSubscription("another/topic/filter", "g1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "g1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2"), "g1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("client")).thenReturn(getClientSessionInfo("client", ClientType.DEVICE, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c2")).thenReturn(getClientSessionInfo("c2", ClientType.APPLICATION, true));

        PageLink pageLink = new PageLink(3, 0, null, new SortOrder("topicFilter", SortOrder.Direction.ASC));
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertEquals(3, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(3, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("another/topic/filter", sharedSubscriptionDto.getTopicFilter());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithSortOrderByShareName_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("client"), "group"))
                ),
                new TopicSharedSubscription("another/topic/filter", "g1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "g1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2"), "g1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("client")).thenReturn(getClientSessionInfo("client", ClientType.DEVICE, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c2")).thenReturn(getClientSessionInfo("c2", ClientType.APPLICATION, true));

        PageLink pageLink = new PageLink(3, 0, null, new SortOrder("shareName", SortOrder.Direction.DESC));
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink));

        assertEquals(3, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(3, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("test/topic/filter", sharedSubscriptionDto.getTopicFilter());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithShareNameSearch_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group1"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("client"), "group1"))
                ),
                new TopicSharedSubscription("another/topic/filter", "p1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "p1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("c2"), "p1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("client")).thenReturn(getClientSessionInfo("client", ClientType.DEVICE, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("c2")).thenReturn(getClientSessionInfo("c2", ClientType.APPLICATION, true));

        PageLink pageLink = new PageLink(3, 0, null, new SortOrder("shareName"));
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink, "p1", null));

        assertEquals(2, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(2, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("group1", sharedSubscriptionDto.getShareName());
        assertEquals(1, sharedSubscriptionDto.getClients().size());

        sharedSubscriptionDto = pageData.getData().get(1);
        assertEquals(2, sharedSubscriptionDto.getClients().size());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithClientIdSearch_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group1"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("clientTest"), "group1"))
                ),
                new TopicSharedSubscription("another/topic/filter", "p1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "p1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("test"), "p1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("clientTest")).thenReturn(getClientSessionInfo("clientTest", ClientType.DEVICE, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, false));
        when(clientSessionCache.getClientSessionInfo("test")).thenReturn(getClientSessionInfo("test", ClientType.APPLICATION, false));

        PageLink pageLink = new PageLink(3, 0, null, new SortOrder("shareName"));
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink, null, "test"));

        assertEquals(2, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(2, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("one/two/three", sharedSubscriptionDto.getTopicFilter());
        assertEquals(1, sharedSubscriptionDto.getClients().size());

        sharedSubscriptionDto = pageData.getData().get(1);
        assertEquals(2, sharedSubscriptionDto.getClients().size());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithSpecifiedAllNotMatchingFilters_thenReturnNothing() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0", ClientType.APPLICATION, true), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group1"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("clientTest", ClientType.DEVICE, true), "group1"))
                ),
                new TopicSharedSubscription("another/topic/filter", "p1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1", ClientType.APPLICATION, false), "p1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("test", ClientType.APPLICATION, false), "p1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);

        PageLink pageLink = new PageLink(3, 0, "/filter", new SortOrder("shareName", SortOrder.Direction.DESC));
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink, "s", "test"));

        assertEquals(0, pageData.getData().size());
        assertEquals(0, pageData.getTotalPages());
        assertEquals(0, pageData.getTotalElements());
        assertFalse(pageData.hasNext());
    }

    @Test
    public void givenSharedSubscriptions_whenExecuteGetSharedSubscriptionsWithSpecifiedAllFilters_thenReturnExpectedResult() {
        Map<TopicSharedSubscription, SharedSubscriptions> map = Map.of(
                new TopicSharedSubscription("test/topic/filter", "s1"), new SharedSubscriptions(
                        Set.of(new Subscription("test/topic/filter", getClientSessionInfo("c0"), "s1")),
                        Set.of()
                ),
                new TopicSharedSubscription("one/two/three", "group1"), new SharedSubscriptions(
                        Set.of(),
                        Set.of(new Subscription("one/two/three", getClientSessionInfo("clientTest"), "group1"))
                ),
                new TopicSharedSubscription("another/topic/filter", "p1"), new SharedSubscriptions(
                        Set.of(
                                new Subscription("another/topic/filter", getClientSessionInfo("c1"), "p1"),
                                new Subscription("another/topic/filter", getClientSessionInfo("test"), "p1")
                        ),
                        Set.of()
                )
        );
        when(sharedSubscriptionCacheService.getAllSharedSubscriptions()).thenReturn(map);
        when(clientSessionCache.getClientSessionInfo("c0")).thenReturn(getClientSessionInfo("c0", ClientType.APPLICATION, true));
        when(clientSessionCache.getClientSessionInfo("clientTest")).thenReturn(getClientSessionInfo("clientTest", ClientType.DEVICE, true));
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(getClientSessionInfo("c1", ClientType.APPLICATION, false));
        when(clientSessionCache.getClientSessionInfo("test")).thenReturn(getClientSessionInfo("test", ClientType.APPLICATION, false));

        PageLink pageLink = new PageLink(3, 0, "/filter", new SortOrder("shareName", SortOrder.Direction.DESC));
        PageData<SharedSubscriptionDto> pageData = sharedSubscriptionPaginationService.getSharedSubscriptions(getSharedSubscriptionQuery(pageLink, "1", "test"));

        assertEquals(1, pageData.getData().size());
        assertEquals(1, pageData.getTotalPages());
        assertEquals(1, pageData.getTotalElements());
        assertFalse(pageData.hasNext());

        SharedSubscriptionDto sharedSubscriptionDto = pageData.getData().get(0);
        assertEquals("another/topic/filter", sharedSubscriptionDto.getTopicFilter());
        assertEquals(2, sharedSubscriptionDto.getClients().size());
        assertNull(sharedSubscriptionDto.getClients().stream().filter(ClientSessionState::isConnected).findAny().orElse(null));
    }

    @NotNull
    private ClientSessionInfo getClientSessionInfo(String clientId, ClientType clientType, boolean connected) {
        return ClientSessionInfo.builder().clientId(clientId).type(clientType).connected(connected).build();
    }

    @NotNull
    private ClientSessionInfo getClientSessionInfo(String clientId) {
        return ClientSessionInfo.builder().clientId(clientId).build();
    }

    @NotNull
    private SharedSubscriptionQuery getSharedSubscriptionQuery(PageLink pageLink) {
        return getSharedSubscriptionQuery(pageLink, null, null);
    }

    @NotNull
    private SharedSubscriptionQuery getSharedSubscriptionQuery(PageLink pageLink, String shareNameSearch, String clientIdSearch) {
        return new SharedSubscriptionQuery(pageLink, shareNameSearch, clientIdSearch);
    }
}
