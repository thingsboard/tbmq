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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionQuery;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientSessionInfo;

@RunWith(MockitoJUnitRunner.class)
public class ClientSessionPageInfosImplTest {

    ClientSessionCache clientSessionCache;
    ClientSubscriptionCache clientSubscriptionCache;
    ClientSessionPageInfosImpl clientSessionPageInfos;

    @Before
    public void setUp() {
        clientSessionCache = mock(ClientSessionCache.class);
        clientSubscriptionCache = mock(ClientSubscriptionCache.class);
        clientSessionPageInfos = spy(new ClientSessionPageInfosImpl(clientSessionCache, clientSubscriptionCache));

        Map<String, ClientSessionInfo> clientSessionInfoMap = getClientSessionInfoMap();
        doReturn(clientSessionInfoMap).when(clientSessionCache).getAllClientSessions();
    }

    @After
    public void destroy() {
    }

    private Map<String, ClientSessionInfo> getClientSessionInfoMap() {
        return Map.of("clientId1", getClientSessionInfo("clientId1"),
                "clientId5", getClientSessionInfo("clientId5"),
                "clientId2", getClientSessionInfo("clientId2"),
                "clientId4", getClientSessionInfo("clientId4"),
                "clientId3", getClientSessionInfo("clientId3"),
                "test1", getClientSessionInfo("test1"),
                "test2", getClientSessionInfo("test2"),
                "test5", getClientSessionInfo("test5"),
                "test4", getClientSessionInfo("test4"),
                "test3", getClientSessionInfo("test3"));
    }

    @Test
    public void testGetClientSessionInfosWithPageSizeAndPage() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(5, 0));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(5, data.size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(2, clientSessionInfos.getTotalPages());
        assertTrue(clientSessionInfos.hasNext());
    }

    @Test
    public void testGetClientSessionInfosWithPageSizePageAndTextSearch() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, "test"));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(5, data.size());
        assertEquals(5, clientSessionInfos.getTotalElements());
        assertEquals(1, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());
    }

    @Test
    public void testGetClientSessionInfosWithPageLink() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, null, new SortOrder("clientId")));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(10, data.size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(1, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        assertEquals("clientId5", data.get(4).getClientId());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, null, new SortOrder("clientId", SortOrder.Direction.DESC)));
        data = clientSessionInfos.getData();

        assertEquals("test1", data.get(4).getClientId());
    }

    @Test
    public void testGetClientSessionInfosWithNotExistedProperty() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, null, new SortOrder("wrongProperty")));
        assertNotNull(clientSessionInfos);
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(1, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());
    }

    @Test
    public void testPaginationResponse() {
        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", getClientSessionInfo("clientId1"),
                "clientId5", getClientSessionInfo("clientId5"),
                "clientId2", getClientSessionInfo("clientId2"),
                "clientId4", getClientSessionInfo("clientId4"),
                "clientId3", getClientSessionInfo("clientId3"),
                "test1", getClientSessionInfo("test1"),
                "test2", getClientSessionInfo("test2"),
                "test5", getClientSessionInfo("test5"),
                "test4", getClientSessionInfo("test4"),
                "test3", getClientSessionInfo("test3")
        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(3, 0));
        assertNotNull(clientSessionInfos);
        assertEquals(3, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(4, clientSessionInfos.getTotalPages());
        assertTrue(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(3, 3));
        assertNotNull(clientSessionInfos);
        assertEquals(1, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(4, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(3, 4));
        assertNotNull(clientSessionInfos);
        assertEquals(0, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(4, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(1, 2));
        assertNotNull(clientSessionInfos);
        assertEquals(1, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(10, clientSessionInfos.getTotalPages());
        assertTrue(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(1, 11));
        assertNotNull(clientSessionInfos);
        assertEquals(0, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(10, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(4, 2));
        assertNotNull(clientSessionInfos);
        assertEquals(2, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(3, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(6, 1));
        assertNotNull(clientSessionInfos);
        assertEquals(4, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(2, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(6, 2));
        assertNotNull(clientSessionInfos);
        assertEquals(0, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(2, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(new PageLink(7, 0));
        assertNotNull(clientSessionInfos);
        assertEquals(7, clientSessionInfos.getData().size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertEquals(2, clientSessionInfos.getTotalPages());
        assertTrue(clientSessionInfos.hasNext());
    }

    @Test
    public void testGetClientSessionInfosWithPageLinkAndSortingBySubscriptionsCount() {
        when(clientSubscriptionCache.getClientSubscriptions("clientId1")).thenReturn(Set.of(getTopicSubscription()));
        when(clientSubscriptionCache.getClientSubscriptions("clientId2")).thenReturn(Set.of(getTopicSubscription(), getTopicSubscription()));
        when(clientSubscriptionCache.getClientSubscriptions("clientId3")).thenReturn(Set.of(getTopicSubscription()));
        when(clientSubscriptionCache.getClientSubscriptions("clientId4")).thenReturn(Set.of());
        when(clientSubscriptionCache.getClientSubscriptions("clientId5")).thenReturn(Set.of(getTopicSubscription(), getTopicSubscription(), getTopicSubscription()));

        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, "clientId", new SortOrder("subscriptionsCount")));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(5, data.size());
        assertEquals(5, clientSessionInfos.getTotalElements());
        assertEquals(1, clientSessionInfos.getTotalPages());
        assertFalse(clientSessionInfos.hasNext());

        assertEquals("clientId4", data.get(0).getClientId());
        assertEquals("clientId5", data.get(4).getClientId());
    }

    @Test
    public void testGetClientSessionInfosWithStartAndEndTimes() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo(false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        long startTime = convertStringToTimestamp("2023-10-10 12:00:00");
        long endTime = convertStringToTimestamp("2023-10-11 12:00:00");

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0, null, null, startTime, endTime))
                .build();

        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);

        assertEquals(2, clientSessionInfos.getData().size());
    }

    @Test
    public void testGetClientSessionInfosWithConnectedStatusList() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo(false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .connectedStatusList(List.of())
                .build();
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(3, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .connectedStatusList(List.of(ConnectionState.CONNECTED, ConnectionState.DISCONNECTED))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(3, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .connectedStatusList(List.of(ConnectionState.DISCONNECTED))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(2, clientSessionInfos.getData().size());
    }

    @Test
    public void testGetClientSessionInfosWithClientTypeList() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo(false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(false, "tbmq2", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-11-11 12:00:00"));
        ClientSessionInfo clientSessionInfo4 = getClientSessionInfo(true, "tbmq3", false,
                ClientType.APPLICATION, convertStringToTimestamp("2023-10-09 16:00:00"), 0);

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3,
                "clientId2", clientSessionInfo2,
                "clientId4", clientSessionInfo4

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .clientTypeList(List.of())
                .build();
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(5, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .clientTypeList(List.of(ClientType.DEVICE, ClientType.APPLICATION))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(5, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .clientTypeList(List.of(ClientType.APPLICATION))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(3, clientSessionInfos.getData().size());
    }

    @Test
    public void testGetClientSessionInfosWithCleanStartList() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo(false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(false, "tbmq2", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-11-11 12:00:00"));
        ClientSessionInfo clientSessionInfo4 = getClientSessionInfo(true, "tbmq3", false,
                ClientType.APPLICATION, convertStringToTimestamp("2023-10-09 16:00:00"), 0);

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3,
                "clientId2", clientSessionInfo2,
                "clientId4", clientSessionInfo4

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .cleanStartList(List.of())
                .build();
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(5, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .cleanStartList(List.of(true, false))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(5, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .cleanStartList(List.of(true))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(2, clientSessionInfos.getData().size());
    }

    @Test
    public void testGetClientSessionInfosWithNodeIdList() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo(false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(false, "tbmq2", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-11-11 12:00:00"));
        ClientSessionInfo clientSessionInfo4 = getClientSessionInfo(true, "tbmq3", false,
                ClientType.APPLICATION, convertStringToTimestamp("2023-10-09 16:00:00"), 0);

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3,
                "clientId2", clientSessionInfo2,
                "clientId4", clientSessionInfo4

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .nodeIdList(List.of())
                .build();
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(5, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .nodeIdList(List.of("tbmq1"))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(3, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .nodeIdList(List.of("tbmq3", "tbmq2"))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(2, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .nodeIdList(List.of("tbmq5"))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(0, clientSessionInfos.getData().size());
    }

    @Test
    public void testGetClientSessionInfosWithSubscriptions() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo("clientId1", true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo("clientId5", false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo("clientId3", false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo("clientId2", false, "tbmq2", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-11-11 12:00:00"));
        ClientSessionInfo clientSessionInfo4 = getClientSessionInfo("clientId4", true, "tbmq3", false,
                ClientType.APPLICATION, convertStringToTimestamp("2023-10-09 16:00:00"), 0);

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3,
                "clientId2", clientSessionInfo2,
                "clientId4", clientSessionInfo4

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        when(clientSubscriptionCache.getClientSubscriptions("clientId1")).thenReturn(Set.of(new TopicSubscription("tf1", 1)));
        when(clientSubscriptionCache.getClientSubscriptions("clientId2")).thenReturn(Set.of(new TopicSubscription("tf2", 2)));
        when(clientSubscriptionCache.getClientSubscriptions("clientId3")).thenReturn(Set.of(new TopicSubscription("tf3", 0)));
        when(clientSubscriptionCache.getClientSubscriptions("clientId4")).thenReturn(Set.of(
                new TopicSubscription("tf4", 1),
                new TopicSubscription("tf44", 1)
        ));
        when(clientSubscriptionCache.getClientSubscriptions("clientId5")).thenReturn(Set.of(
                new TopicSubscription("tf5", 1),
                new TopicSubscription("tf55", 0),
                new TopicSubscription("tf555", 2)
        ));

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .subscriptions(2)
                .build();
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(1, clientSessionInfos.getData().size());
        assertEquals("clientId4", clientSessionInfos.getData().get(0).getClientId());
    }

    @Test
    public void testGetClientSessionInfosWithQuery() {
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(true, "tbmq1", true,
                ClientType.DEVICE, convertStringToTimestamp("2023-10-10 13:00:00"), 0);
        ClientSessionInfo clientSessionInfo5 = getClientSessionInfo(false, "tbmq1", true,
                ClientType.DEVICE, 0, convertStringToTimestamp("2023-10-11 13:00:00"));
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(false, "tbmq1", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-10-11 12:00:00"));
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(false, "tbmq2", false,
                ClientType.APPLICATION, 0, convertStringToTimestamp("2023-11-11 12:00:00"));
        ClientSessionInfo clientSessionInfo4 = getClientSessionInfo(true, "tbmq3", false,
                ClientType.APPLICATION, convertStringToTimestamp("2023-10-09 16:00:00"), 0);

        Map<String, ClientSessionInfo> map = Map.of(
                "clientId1", clientSessionInfo1,
                "clientId5", clientSessionInfo5,
                "clientId3", clientSessionInfo3,
                "clientId2", clientSessionInfo2,
                "clientId4", clientSessionInfo4

        );
        doReturn(map).when(clientSessionCache).getAllClientSessions();

        ClientSessionQuery clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .connectedStatusList(List.of(ConnectionState.CONNECTED))
                .cleanStartList(List.of(false))
                .clientTypeList(List.of(ClientType.APPLICATION))
                .nodeIdList(List.of("tbmq2"))
                .build();
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(0, clientSessionInfos.getData().size());

        clientSessionQuery = ClientSessionQuery
                .builder()
                .pageLink(new TimePageLink(100, 0))
                .connectedStatusList(List.of(ConnectionState.CONNECTED))
                .cleanStartList(List.of(true))
                .clientTypeList(List.of(ClientType.DEVICE))
                .nodeIdList(List.of("tbmq1"))
                .build();
        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(clientSessionQuery);
        assertEquals(1, clientSessionInfos.getData().size());
    }

    public static long convertStringToTimestamp(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);
        return dateTime.toEpochSecond(OffsetDateTime.now().getOffset()) * 1000;
    }

    private TopicSubscription getTopicSubscription() {
        return new TopicSubscription(RandomStringUtils.randomAlphabetic(10), 1);
    }
}
