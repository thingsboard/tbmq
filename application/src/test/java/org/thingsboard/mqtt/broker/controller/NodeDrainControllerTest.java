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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainService;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainState;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainStatus;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class NodeDrainControllerTest extends AbstractControllerTest {

    @MockitoBean
    private NodeDrainService nodeDrainService;
    @Autowired
    private ServiceInfoProvider serviceInfoProvider;

    private String serviceId;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
        serviceId = serviceInfoProvider.getServiceId();
    }

    @Test
    public void givenActiveNode_whenStartDrain_thenReturnsDrainStatus() throws Exception {
        when(nodeDrainService.startDrain()).thenReturn(drainStatus(NodeDrainState.DRAINING, 42, 42));

        doPost(NodeDrainController.DRAIN_PATH, "expectedServiceId", serviceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAINING"))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.initialSessions").value(42))
                .andExpect(jsonPath("$.remainingSessions").value(42));

        verify(nodeDrainService).startDrain();
    }

    @Test
    public void givenDrainInProgress_whenCancelDrain_thenReturnsActiveStatus() throws Exception {
        when(nodeDrainService.cancelDrain()).thenReturn(drainStatus(NodeDrainState.ACTIVE, 0, 7));

        doDelete(NodeDrainController.DRAIN_PATH, "expectedServiceId", serviceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.serviceId").value(serviceId));

        verify(nodeDrainService).cancelDrain();
    }

    @Test
    public void givenWrongTarget_whenStartDrain_thenRejectsWithBadRequestWithoutStarting() throws Exception {
        doPost(NodeDrainController.DRAIN_PATH, "expectedServiceId", "other-service")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(31))
                .andExpect(jsonPath("$.message").value(containsString("reached service '" + serviceId + "'")));

        verifyNoInteractions(nodeDrainService);
    }

    @Test
    public void givenMissingTarget_whenStartDrain_thenRejectsWithBadRequestWithoutStarting() throws Exception {
        doPost(NodeDrainController.DRAIN_PATH)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(31));

        verifyNoInteractions(nodeDrainService);
    }

    @Test
    public void givenDrainInProgress_whenGetStatus_thenReturnsCurrentProgress() throws Exception {
        when(nodeDrainService.getStatus()).thenReturn(drainStatus(NodeDrainState.DRAINING, 42, 7));

        doGet(NodeDrainController.DRAIN_PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAINING"))
                .andExpect(jsonPath("$.remainingSessions").value(7));

        verify(nodeDrainService).getStatus();
    }

    private NodeDrainStatus drainStatus(NodeDrainState state, int initialSessions, int remainingSessions) {
        return new NodeDrainStatus(serviceId, state, initialSessions, remainingSessions, initialSessions - remainingSessions,
                1_000L, 0L);
    }

}
