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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.ResourceUsageAlertSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.audit.ActionType;
import org.thingsboard.mqtt.broker.common.data.audit.AuditLog;
import org.thingsboard.mqtt.broker.common.data.audit.EntityType;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.audit.AuditLogService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.mail.MailService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;

@ExtendWith(MockitoExtension.class)
class DefaultResourceUsageAlertServiceTest {

    private static final String SETTINGS_KEY = SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey();
    private static final String TBMQ = "TBMQ";
    private static final String EXECUTOR = "TBMQ_INTEGRATION_EXECUTOR";

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
    }

    @Test
    void givenNoStoredSettings_whenUsageCrossesDefaultThresholds_thenAuditsOncePerCrossing() {
        givenStoredSettings(null);

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 90L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 92L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 40L));

        List<AuditLog> logs = capturedAuditLogs(2);
        assertEquals(ActionType.RESOURCE_USAGE_ALERT, logs.get(0).getActionType());
        assertEquals(EntityType.SERVICE_INFO, logs.get(0).getEntityType());
        assertEquals(ServiceInfoIds.auditEntityId("tbmq1", TBMQ), logs.get(0).getEntityId());
        assertEquals("tbmq1", logs.get(0).getEntityName());
        assertNull(logs.get(0).getUserId());
        assertEquals("CRITICAL", logs.get(0).getActionData().get("level").asText());
        assertEquals(90, logs.get(0).getActionData().get("usage").asLong());
        assertEquals(ActionType.RESOURCE_USAGE_RECOVERED, logs.get(1).getActionType());
        verify(mailService, never()).sendResourceUsageAlertEmailAsync(any(), anyList());
    }

    @Test
    void givenUsageBelowThresholds_whenEvaluated_thenNothingIsRecorded() {
        givenStoredSettings(null);

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 10L, MEMORY_USAGE, 50L));

        verify(auditLogService, never()).save(any());
    }

    @Test
    void givenEmailEnabledWithRecipients_whenUsageBecomesHigh_thenMailIsSentToEachRecipient() {
        givenStoredSettings(new ResourceUsageAlertSettings(60, 80, true, "ops@example.com, admin@example.com"));

        service.evaluate("tbmq2", TBMQ, Map.of(MEMORY_USAGE, 70L));

        verify(auditLogService).save(any());
        verify(mailService).sendResourceUsageAlertEmailAsync(
                new ResourceUsageAlert("tbmq2", TBMQ, MEMORY_USAGE, 70L, "HIGH", 60),
                List.of("ops@example.com", "admin@example.com"));
        verify(userService, never()).findUsers(any());
    }

    @Test
    void givenEmailEnabledWithoutRecipients_whenAlert_thenEveryUserOnEveryPageIsNotified() {
        givenStoredSettings(new ResourceUsageAlertSettings(60, 80, true, " "));
        when(userService.findUsers(any(PageLink.class))).thenAnswer(invocation -> {
            PageLink pageLink = invocation.getArgument(0);
            return pageLink.getPage() == 0
                    ? new PageData<>(List.of(user("a@example.com"), user(null)), 2, 3, true)
                    : new PageData<>(List.of(user("b@example.com")), 2, 3, false);
        });

        service.evaluate("tbmq2", TBMQ, Map.of(CPU_USAGE, 90L));

        verify(userService, times(2)).findUsers(any(PageLink.class));
        verify(mailService).sendResourceUsageAlertEmailAsync(
                new ResourceUsageAlert("tbmq2", TBMQ, CPU_USAGE, 90L, "CRITICAL", 80),
                List.of("a@example.com", "b@example.com"));
    }

    @Test
    void givenInvalidStoredJson_whenEvaluated_thenDefaultsApply() {
        AdminSettings stored = new AdminSettings();
        stored.setKey(SETTINGS_KEY);
        stored.setJsonValue(JacksonUtil.toJsonNode("{\"highUsage\":\"not-a-number\"}"));
        when(adminSettingsService.findAdminSettingsByKey(SETTINGS_KEY)).thenReturn(stored);

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, ResourceUsageAlertSettings.DEFAULT_HIGH_USAGE + 1L));

        List<AuditLog> logs = capturedAuditLogs(1);
        assertEquals("HIGH", logs.get(0).getActionData().get("level").asText());
        assertEquals(ResourceUsageAlertSettings.DEFAULT_HIGH_USAGE, logs.get(0).getActionData().get("highUsage").asInt());
    }

    @Test
    void givenHigh_whenUsageRisesAboveCritical_thenAlertsAgain() {
        givenStoredSettings(new ResourceUsageAlertSettings(75, 85, true, "ops@example.com"));

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 80L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 90L));

        List<AuditLog> logs = capturedAuditLogs(2);
        assertThat(logs).extracting(AuditLog::getActionType).containsOnly(ActionType.RESOURCE_USAGE_ALERT);
        assertThat(logs).extracting(log -> log.getActionData().get("level").asText()).containsExactly("HIGH", "CRITICAL");
        verify(mailService, times(2)).sendResourceUsageAlertEmailAsync(any(), anyList());
    }

    @Test
    void givenCritical_whenUsageFallsToHigh_thenStateChangesSilently() {
        givenStoredSettings(new ResourceUsageAlertSettings(75, 85, true, "ops@example.com"));

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 90L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 78L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 77L));

        List<AuditLog> logs = capturedAuditLogs(1);
        assertEquals("CRITICAL", logs.get(0).getActionData().get("level").asText());
        verify(mailService, times(1)).sendResourceUsageAlertEmailAsync(any(), anyList());

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 90L));

        verify(auditLogService, times(2)).save(any());
    }

    @Test
    void givenHighThreshold_whenUsageHoversAroundIt_thenRecoveryNeedsTheMargin() {
        givenStoredSettings(new ResourceUsageAlertSettings(75, 85, false, null));

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 76L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 72L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 74L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 69L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 75L));

        List<AuditLog> logs = capturedAuditLogs(2);
        assertEquals(ActionType.RESOURCE_USAGE_ALERT, logs.get(0).getActionType());
        assertEquals(76, logs.get(0).getActionData().get("usage").asLong());
        assertEquals(ActionType.RESOURCE_USAGE_RECOVERED, logs.get(1).getActionType());
        assertEquals(69, logs.get(1).getActionData().get("usage").asLong());
    }

    @Test
    void givenCriticalThreshold_whenUsageDipsWithinTheMargin_thenStaysCritical() {
        givenStoredSettings(new ResourceUsageAlertSettings(75, 85, false, null));

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 86L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 81L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 86L));

        assertThat(capturedAuditLogs(1)).extracting(log -> log.getActionData().get("level").asText()).containsExactly("CRITICAL");
    }

    @Test
    void givenSeveralNodesAndMetrics_whenEvaluated_thenEachIsTrackedSeparately() {
        givenStoredSettings(null);

        service.evaluate("node1", TBMQ, Map.of(CPU_USAGE, 90L));
        service.evaluate("node2", TBMQ, Map.of(CPU_USAGE, 90L));
        service.evaluate("node1", EXECUTOR, Map.of(CPU_USAGE, 90L));
        service.evaluate("node1", TBMQ, Map.of(MEMORY_USAGE, 90L));
        service.evaluate("node1", TBMQ, Map.of(CPU_USAGE, 91L, MEMORY_USAGE, 91L));

        List<AuditLog> logs = capturedAuditLogs(4);
        assertThat(logs).extracting(AuditLog::getActionType).containsOnly(ActionType.RESOURCE_USAGE_ALERT);
        assertThat(logs).extracting(AuditLog::getEntityId).containsExactly(
                ServiceInfoIds.auditEntityId("node1", TBMQ),
                ServiceInfoIds.auditEntityId("node2", TBMQ),
                ServiceInfoIds.auditEntityId("node1", EXECUTOR),
                ServiceInfoIds.auditEntityId("node1", TBMQ));
    }

    @Test
    void givenCachedSettings_whenEvaluatedRepeatedly_thenSettingsAreReadOncePerTtlOrUpdate() {
        givenStoredSettings(null);

        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 10L));
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 10L));
        verify(adminSettingsService, times(1)).findAdminSettingsByKey(SETTINGS_KEY);

        service.onSettingsUpdate();
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 10L));
        verify(adminSettingsService, times(2)).findAdminSettingsByKey(SETTINGS_KEY);

        service.settingsCacheTtlMs = 0;
        service.evaluate("tbmq1", TBMQ, Map.of(CPU_USAGE, 10L));
        verify(adminSettingsService, times(3)).findAdminSettingsByKey(SETTINGS_KEY);
    }

    @Test
    void givenNoStoredSettings_whenGetAdminSettings_thenDefaultsWithoutId() {
        givenStoredSettings(null);

        AdminSettings adminSettings = service.getAdminSettings();

        assertNull(adminSettings.getId());
        assertEquals(SETTINGS_KEY, adminSettings.getKey());
        assertEquals(JacksonUtil.valueToTree(ResourceUsageAlertSettings.defaults()), adminSettings.getJsonValue());
    }

    @Test
    void givenPartialStoredSettings_whenGetAdminSettings_thenMissingValuesAreDefaultsAndIdIsKept() {
        AdminSettings stored = new AdminSettings(UUID.randomUUID());
        stored.setKey(SETTINGS_KEY);
        stored.setJsonValue(JacksonUtil.toJsonNode("{\"highUsage\":60}"));
        when(adminSettingsService.findAdminSettingsByKey(SETTINGS_KEY)).thenReturn(stored);

        AdminSettings adminSettings = service.getAdminSettings();

        assertEquals(stored.getId(), adminSettings.getId());
        JsonNode json = adminSettings.getJsonValue();
        assertEquals(60, json.get("highUsage").asInt());
        assertEquals(ResourceUsageAlertSettings.DEFAULT_CRITICAL_USAGE, json.get("criticalUsage").asInt());
    }

    private void givenStoredSettings(ResourceUsageAlertSettings settings) {
        AdminSettings stored = null;
        if (settings != null) {
            stored = new AdminSettings();
            stored.setKey(SETTINGS_KEY);
            stored.setJsonValue(JacksonUtil.valueToTree(settings));
        }
        when(adminSettingsService.findAdminSettingsByKey(SETTINGS_KEY)).thenReturn(stored);
    }

    private List<AuditLog> capturedAuditLogs(int expectedCount) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        return user;
    }

}
