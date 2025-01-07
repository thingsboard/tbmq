/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.flow.control;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtxImpl;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class FlowControlServiceImplTest {

    FlowControlServiceImpl flowControlService;
    PublishedInFlightCtxImpl publishedInFlightCtx;

    @Before
    public void setUp() throws Exception {
        flowControlService = spy(new FlowControlServiceImpl());
        flowControlService.setFlowControlEnabled(true);
        flowControlService.setTimeout(1000);
        flowControlService.setClientsWithDelayedMsgMap(new ConcurrentHashMap<>());
        flowControlService.setService(ThingsBoardExecutors.initExecutorService(1, "flow-control-executor"));

        publishedInFlightCtx = mock(PublishedInFlightCtxImpl.class);
    }

    @After
    public void tearDown() throws Exception {
        flowControlService.destroy();
    }

    @Test
    public void givenClientAndCtxNull_whenAddToMap_thenNothingAdded() {
        flowControlService.addToMap(null, null);

        assertTrue(flowControlService.getClientsWithDelayedMsgMap().isEmpty());
    }

    @Test
    public void givenClientAndCtxPresent_whenAddToMap_thenEntryAdded() {
        flowControlService.addToMap("test", publishedInFlightCtx);

        assertEquals(1, flowControlService.getClientsWithDelayedMsgMap().size());
    }

    @Test
    public void givenFlowControlDisabledAndClientAndCtxPresent_whenAddToMap_thenEntryNotAdded() {
        flowControlService.setFlowControlEnabled(false);
        flowControlService.addToMap("test", publishedInFlightCtx);

        assertTrue(flowControlService.getClientsWithDelayedMsgMap().isEmpty());
    }

    @Test
    public void givenClientNull_whenRemoveFromMap_thenNothingChanged() {
        flowControlService.removeFromMap(null);

        assertTrue(flowControlService.getClientsWithDelayedMsgMap().isEmpty());
    }

}
