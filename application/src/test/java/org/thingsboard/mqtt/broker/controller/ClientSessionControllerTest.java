/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.permission.Operation;
import org.thingsboard.mqtt.broker.dto.ClientSessionNodeStatsInfoDto;
import org.thingsboard.mqtt.broker.service.entity.clientsession.TbClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPageInfos;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.SessionSubscriptionService;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.permission.AccessControlService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class ClientSessionControllerTest {

    @Mock
    private SessionSubscriptionService sessionSubscriptionService;
    @Mock
    private ClientSessionPageInfos clientSessionPageInfos;
    @Mock
    private TbCacheOps cacheOps;
    @Mock
    private TbClientSessionService tbClientSessionService;
    @Mock
    private AccessControlService accessControlService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClientSessionController controller = new ClientSessionController(
                sessionSubscriptionService, clientSessionPageInfos, cacheOps, tbClientSessionService);
        ReflectionTestUtils.setField(controller, "accessControlService", accessControlService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(mock(SecurityUser.class), null);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void givenSessionsOnTwoNodes_whenGetStatsByNode_thenJsonKeyedByNodeId() throws Exception {
        when(clientSessionPageInfos.getClientSessionStatsInfoByNode()).thenReturn(Map.of(
                "tbmq1", new ClientSessionNodeStatsInfoDto(1, 1, 2, 2, 0, 0),
                "tbmq2", new ClientSessionNodeStatsInfoDto(1, 0, 1, 0, 1, 0)));

        mockMvc.perform(get("/api/client-session/info/by-node"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tbmq1.connectedCount").value(1))
                .andExpect(jsonPath("$.tbmq1.disconnectedCount").value(1))
                .andExpect(jsonPath("$.tbmq1.totalCount").value(2))
                .andExpect(jsonPath("$.tbmq1.deviceCount").value(2))
                .andExpect(jsonPath("$.tbmq2.applicationCount").value(1))
                .andExpect(jsonPath("$.tbmq2.connectedCount").value(1))
                .andExpect(jsonPath("$.tbmq2.totalCount").value(1));

        verify(accessControlService).checkPermission(any(), eq(Operation.READ));
    }

    @Test
    void givenNoSessions_whenGetStatsByNode_thenEmptyJsonObject() throws Exception {
        when(clientSessionPageInfos.getClientSessionStatsInfoByNode()).thenReturn(Map.of());

        mockMvc.perform(get("/api/client-session/info/by-node"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }
}
