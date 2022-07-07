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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ApplicationMsgInfo;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationPersistedMsgCtxServiceImplTest {

    static final String CLIENT_ID = "clientId";
    static final long TS = 1657202435000L;

    ApplicationSessionCtxService sessionCtxService;
    ApplicationPersistedMsgCtxServiceImpl applicationPersistedMsgCtxService;

    @Before
    public void setUp() throws Exception {
        sessionCtxService = mock(ApplicationSessionCtxService.class);
        applicationPersistedMsgCtxService = spy(new ApplicationPersistedMsgCtxServiceImpl(sessionCtxService));
    }

    @Test
    public void loadEmptyPersistedMsgCtxTest() {
        when(sessionCtxService.findApplicationSessionCtx(CLIENT_ID)).thenReturn(Optional.empty());

        ApplicationPersistedMsgCtx applicationPersistedMsgCtx = applicationPersistedMsgCtxService.loadPersistedMsgCtx(CLIENT_ID);
        Assert.assertTrue(applicationPersistedMsgCtx.getPublishMsgIds().isEmpty());
        Assert.assertTrue(applicationPersistedMsgCtx.getPubRelMsgIds().isEmpty());
    }

    @Test
    public void loadPersistedMsgCtxTest() {
        ApplicationSessionCtx applicationSessionCtx = buildApplicationSessionCtx();
        when(sessionCtxService.findApplicationSessionCtx(CLIENT_ID)).thenReturn(Optional.of(applicationSessionCtx));

        ApplicationPersistedMsgCtx actual = applicationPersistedMsgCtxService.loadPersistedMsgCtx(CLIENT_ID);
        ApplicationPersistedMsgCtx expected = new ApplicationPersistedMsgCtx(getPendingMsgIdsMap(), getPendingMsgIdsMap());
        Assert.assertEquals(expected, actual);
    }

    private ApplicationSessionCtx buildApplicationSessionCtx() {
        return ApplicationSessionCtx.builder()
                .clientId(CLIENT_ID)
                .lastUpdatedTime(TS)
                .publishMsgInfos(getApplicationMsgInfos())
                .pubRelMsgInfos(getApplicationMsgInfos())
                .build();
    }

    private List<ApplicationMsgInfo> getApplicationMsgInfos() {
        return List.of(newApplicationMsgInfo(100L, 100), newApplicationMsgInfo(200L, 200));
    }

    private ApplicationMsgInfo newApplicationMsgInfo(long offset, int packetId) {
        return new ApplicationMsgInfo(offset, packetId);
    }

    private Map<Long, Integer> getPendingMsgIdsMap() {
        return Map.of(100L, 100, 200L, 200);
    }

    @Test
    public void saveContextTest() {
        ApplicationPackProcessingCtx applicationPackProcessingCtx = mock(ApplicationPackProcessingCtx.class);

        when(applicationPackProcessingCtx.getPublishPendingMsgMap())
                .thenReturn(new ConcurrentHashMap<>(Map.of(1, new PersistedPublishMsg(buildPubMsg(), 100))));
        when(applicationPackProcessingCtx.getPubRelPendingMsgMap())
                .thenReturn(new ConcurrentHashMap<>(Map.of(1, new PersistedPubRelMsg(1, 100))));
        when(applicationPackProcessingCtx.getNewPubRelPackets())
                .thenReturn(List.of(new PersistedPubRelMsg(2, 200), new PersistedPubRelMsg(3, 300)));

        applicationPersistedMsgCtxService.saveContext(CLIENT_ID, applicationPackProcessingCtx);

        ArgumentCaptor<ApplicationSessionCtx> applicationSessionCtxCaptor = ArgumentCaptor.forClass(ApplicationSessionCtx.class);
        verify(sessionCtxService, times(1)).saveApplicationSessionCtx(applicationSessionCtxCaptor.capture());
        ApplicationSessionCtx applicationSessionCtx = applicationSessionCtxCaptor.getValue();

        Assert.assertEquals(CLIENT_ID, applicationSessionCtx.getClientId());
        Assert.assertTrue(applicationSessionCtx.getLastUpdatedTime() > 0);
        Assert.assertEquals(List.of(new ApplicationMsgInfo(100, 1)), applicationSessionCtx.getPublishMsgInfos());
        Assert.assertEquals(
                List.of(
                        new ApplicationMsgInfo(100, 1),
                        new ApplicationMsgInfo(200, 2),
                        new ApplicationMsgInfo(300, 3)),
                applicationSessionCtx.getPubRelMsgInfos());
    }

    private PublishMsg buildPubMsg() {
        return PublishMsg.builder()
                .packetId(1)
                .topicName("topic")
                .payload("data".getBytes())
                .qosLevel(1)
                .isRetained(false)
                .isDup(false)
                .build();
    }

    @Test
    public void saveNullContextTest() {
        applicationPersistedMsgCtxService.saveContext(CLIENT_ID, null);
        verify(sessionCtxService, times(0)).saveApplicationSessionCtx(any());
    }

    @Test
    public void clearContextTest() {
        applicationPersistedMsgCtxService.clearContext(CLIENT_ID);
        verify(sessionCtxService, times(1)).deleteApplicationSessionCtx(eq(CLIENT_ID));
    }
}