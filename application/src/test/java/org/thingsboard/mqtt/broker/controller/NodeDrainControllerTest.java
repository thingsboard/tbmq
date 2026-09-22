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
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainService;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainState;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainStatus;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@RunWith(MockitoJUnitRunner.class)
public class NodeDrainControllerTest {

    @Mock
    private NodeDrainService nodeDrainService;
    @InjectMocks
    private NodeDrainController controller;
    private MockMvc mockMvc;

    @Before
    public void beforeTest() {
        mockMvc = standaloneSetup(controller).build();
    }

    @Test
    public void givenActiveNode_whenStartDrain_thenReturnsDrainStatus() throws Exception {
        when(nodeDrainService.startDrain()).thenReturn(drainStatus(NodeDrainState.DRAINING, 42, 42));

        mockMvc.perform(post(NodeDrainController.DRAIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAINING"))
                .andExpect(jsonPath("$.initialSessions").value(42))
                .andExpect(jsonPath("$.remainingSessions").value(42));

        verify(nodeDrainService).startDrain();
    }

    @Test
    public void givenDrainInProgress_whenGetStatus_thenReturnsCurrentProgress() throws Exception {
        when(nodeDrainService.getStatus()).thenReturn(drainStatus(NodeDrainState.DRAINING, 42, 7));

        mockMvc.perform(get(NodeDrainController.DRAIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAINING"))
                .andExpect(jsonPath("$.remainingSessions").value(7));

        verify(nodeDrainService).getStatus();
    }

    private static NodeDrainStatus drainStatus(NodeDrainState state, int initialSessions, int remainingSessions) {
        return new NodeDrainStatus(state, initialSessions, remainingSessions, initialSessions - remainingSessions,
                1_000L, 0L);
    }

}
