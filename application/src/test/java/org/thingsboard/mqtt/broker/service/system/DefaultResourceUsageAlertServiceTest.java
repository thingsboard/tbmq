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
package org.thingsboard.mqtt.broker.service.system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.ResourceUsageAlertSettings;
import org.thingsboard.mqtt.broker.common.data.audit.ActionType;
import org.thingsboard.mqtt.broker.common.data.audit.AuditLog;
import org.thingsboard.mqtt.broker.common.data.audit.EntityType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.audit.AuditLogService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.mail.MailService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;

@ExtendWith(MockitoExtension.class)
class DefaultResourceUsageAlertServiceTest {

    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private MailService mailService;
    @Mock
    private UserService userService;

    private DefaultResourceUsageAlertService service;

    @BeforeEach
    void setUp() {
        service = new DefaultResourceUsageAlertService(adminSettingsService, auditLogService, mailService, userService);
        service.init();
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void givenNoStoredSettings_whenUsageCrossesDefaultThresholds_thenAuditsOncePerCrossing() throws Exception {
        when(adminSettingsService.findAdminSettingsByKey(anyString())).thenReturn(null);

        service.evaluate("tbmq1", "TBMQ", Map.of(CPU_USAGE, 90L));
        service.evaluate("tbmq1", "TBMQ", Map.of(CPU_USAGE, 92L));
        service.evaluate("tbmq1", "TBMQ", Map.of(CPU_USAGE, 40L));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService, times(2)).save(captor.capture());
        List<AuditLog> logs = captor.getAllValues();
        assertEquals(ActionType.RESOURCE_USAGE_ALERT, logs.get(0).getActionType());
        assertEquals(EntityType.SERVICE_INFO, logs.get(0).getEntityType());
        assertEquals("tbmq1", logs.get(0).getEntityName());
        assertNull(logs.get(0).getUserId());
        assertEquals("CRITICAL", logs.get(0).getActionData().get("level").asText());
        assertEquals(90, logs.get(0).getActionData().get("usage").asLong());
        assertEquals(ActionType.RESOURCE_USAGE_RECOVERED, logs.get(1).getActionType());
        verify(mailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void givenUsageBelowThresholds_whenEvaluated_thenNothingIsRecorded() {
        when(adminSettingsService.findAdminSettingsByKey(anyString())).thenReturn(null);

        service.evaluate("tbmq1", "TBMQ", Map.of(CPU_USAGE, 10L, MEMORY_USAGE, 50L));

        verify(auditLogService, never()).save(any());
    }

    @Test
    void givenEmailEnabledWithRecipients_whenUsageBecomesHigh_thenMailIsSentToEachRecipient() throws Exception {
        ResourceUsageAlertSettings settings = new ResourceUsageAlertSettings(60, 80, true, "ops@example.com, admin@example.com");
        AdminSettings stored = new AdminSettings();
        stored.setKey("resourceUsageAlerts");
        stored.setJsonValue(JacksonUtil.valueToTree(settings));
        when(adminSettingsService.findAdminSettingsByKey("resourceUsageAlerts")).thenReturn(stored);

        service.evaluate("tbmq2", "TBMQ", Map.of(MEMORY_USAGE, 70L));
        service.destroy();

        verify(auditLogService).save(any());
        verify(mailService).sendEmail(eq("ops@example.com"), anyString(), anyString());
        verify(mailService).sendEmail(eq("admin@example.com"), anyString(), anyString());
        verify(userService, never()).findUsers(any());
    }

}
