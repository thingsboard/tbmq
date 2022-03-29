/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
class ClientSessionPageInfosImplTest {

    ClientSessionCache clientSessionCache;
    ClientSessionPageInfosImpl clientSessionPageInfos;

    @BeforeEach
    void setUp() {
        clientSessionCache = mock(ClientSessionCache.class);
        clientSessionPageInfos = spy(new ClientSessionPageInfosImpl(clientSessionCache));

        Map<String, ClientSessionInfo> clientSessionInfoMap = getClientSessionInfoMap();
        doReturn(clientSessionInfoMap).when(clientSessionCache).getAllClientSessions();
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

    private ClientSessionInfo getClientSessionInfo(String clientId) {
        ClientInfo clientInfo = ClientInfo.builder().clientId(clientId).type(ClientType.DEVICE).build();
        ConnectionInfo connectionInfo = ConnectionInfo.builder().connectedAt(System.currentTimeMillis()).keepAlive(100000).build();
        SessionInfo sessionInfo = SessionInfo.builder().sessionId(UUID.randomUUID()).serviceId("serviceId").clientInfo(clientInfo).connectionInfo(connectionInfo).build();
        ClientSession clientSession = ClientSession.builder().connected(true).sessionInfo(sessionInfo).build();
        return ClientSessionInfo.builder().lastUpdateTime(System.currentTimeMillis()).clientSession(clientSession).build();
    }

    @Test
    void testGetClientSessionInfosWithPageSizeAndPage() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(5, 0));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(5, data.size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertTrue(clientSessionInfos.hasNext());
    }

    @Test
    void testGetClientSessionInfosWithPageSizePageAndTextSearch() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, "test"));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(5, data.size());
        assertEquals(5, clientSessionInfos.getTotalElements());
        assertFalse(clientSessionInfos.hasNext());
    }

    @Test
    void testGetClientSessionInfosWithPageLink() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, null, new SortOrder("clientId")));
        List<ShortClientSessionInfoDto> data = clientSessionInfos.getData();

        assertEquals(10, data.size());
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertFalse(clientSessionInfos.hasNext());

        assertEquals("clientId5", data.get(4).getClientId());

        clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, null, new SortOrder("clientId", SortOrder.Direction.DESC)));
        data = clientSessionInfos.getData();

        assertEquals("test1", data.get(4).getClientId());
    }

    @Test
    void testGetClientSessionInfosWithNotExistedProperty() {
        PageData<ShortClientSessionInfoDto> clientSessionInfos = clientSessionPageInfos.getClientSessionInfos(
                new PageLink(100, 0, null, new SortOrder("wrongProperty")));
        assertNotNull(clientSessionInfos);
        assertEquals(10, clientSessionInfos.getTotalElements());
        assertFalse(clientSessionInfos.hasNext());
    }
}