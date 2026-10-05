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

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.ResourceUsageAlertSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.audit.ActionStatus;
import org.thingsboard.mqtt.broker.common.data.audit.ActionType;
import org.thingsboard.mqtt.broker.common.data.audit.AuditLog;
import org.thingsboard.mqtt.broker.common.data.audit.EntityType;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.audit.AuditLogService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.mail.MailService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultResourceUsageAlertService implements ResourceUsageAlertService {

    static final String SYSTEM_USER_NAME = "system";
    private static final int MAX_RECIPIENTS = 100;
    private static final Map<String, String> METRIC_NAMES = Map.of(CPU_USAGE, "CPU", MEMORY_USAGE, "Memory", DISK_USAGE, "Disk");

    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final UserService userService;

    // Last band seen per node and metric, so an alert fires once per crossing instead of on every sample.
    private final ConcurrentMap<String, UsageLevel> levels = new ConcurrentHashMap<>();
    private ExecutorService mailExecutor;

    @PostConstruct
    public void init() {
        mailExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("resource-usage-alert-mail"));
    }

    @PreDestroy
    public void destroy() {
        if (mailExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(mailExecutor, "Resource usage alert mail");
        }
    }

    @Override
    public void evaluate(String serviceId, String serviceType, Map<String, Long> usagePercentByMetric) {
        if (usagePercentByMetric == null || usagePercentByMetric.isEmpty()) {
            return;
        }
        ResourceUsageAlertSettings settings = loadSettings();
        usagePercentByMetric.forEach((metric, usage) -> {
            UsageLevel level = levelOf(usage, settings);
            UsageLevel previous = levels.put(serviceType + "|" + serviceId + "|" + metric, level);
            if (previous == null) {
                previous = UsageLevel.NORMAL;
            }
            if (level == previous) {
                return;
            }
            if (level == UsageLevel.NORMAL) {
                audit(ActionType.RESOURCE_USAGE_RECOVERED, serviceId, serviceType, metric, usage, level, settings);
            } else {
                audit(ActionType.RESOURCE_USAGE_ALERT, serviceId, serviceType, metric, usage, level, settings);
                if (settings.isEmailEnabled()) {
                    notifyByEmail(serviceId, metric, usage, level, settings);
                }
            }
        });
    }

    ResourceUsageAlertSettings loadSettings() {
        AdminSettings stored = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        if (stored == null || stored.getJsonValue() == null) {
            return ResourceUsageAlertSettings.defaults();
        }
        try {
            return JacksonUtil.convertValue(stored.getJsonValue(), ResourceUsageAlertSettings.class);
        } catch (Exception e) {
            log.warn("Invalid resource usage alert settings {}, using defaults", stored.getJsonValue(), e);
            return ResourceUsageAlertSettings.defaults();
        }
    }

    static UsageLevel levelOf(long usage, ResourceUsageAlertSettings settings) {
        if (usage > settings.getCriticalUsage()) {
            return UsageLevel.CRITICAL;
        }
        return usage > settings.getHighUsage() ? UsageLevel.HIGH : UsageLevel.NORMAL;
    }

    private void audit(ActionType actionType, String serviceId, String serviceType, String metric, long usage,
                       UsageLevel level, ResourceUsageAlertSettings settings) {
        AuditLog auditLog = new AuditLog(UUID.randomUUID());
        auditLog.setCreatedTime(System.currentTimeMillis());
        auditLog.setEntityId(UUID.nameUUIDFromBytes((serviceType + ":" + serviceId).getBytes(StandardCharsets.UTF_8)));
        auditLog.setEntityType(EntityType.SERVICE_INFO);
        auditLog.setEntityName(serviceId);
        auditLog.setUserName(SYSTEM_USER_NAME);
        auditLog.setActionType(actionType);
        ObjectNode actionData = JacksonUtil.newObjectNode();
        actionData.put("serviceId", serviceId);
        actionData.put("serviceType", serviceType);
        actionData.put("metric", metric);
        actionData.put("usage", usage);
        actionData.put("level", level.name());
        actionData.put("highUsage", settings.getHighUsage());
        actionData.put("criticalUsage", settings.getCriticalUsage());
        auditLog.setActionData(actionData);
        auditLog.setActionStatus(ActionStatus.SUCCESS);
        try {
            auditLogService.save(auditLog);
        } catch (Exception e) {
            log.warn("[{}] Failed to save resource usage audit log for {}", serviceId, metric, e);
        }
    }

    private void notifyByEmail(String serviceId, String metric, long usage, UsageLevel level, ResourceUsageAlertSettings settings) {
        List<String> recipients = recipients(settings);
        if (recipients.isEmpty()) {
            log.debug("[{}] No recipients for resource usage alert", serviceId);
            return;
        }
        String metricName = METRIC_NAMES.getOrDefault(metric, metric);
        String subject = String.format("TBMQ: %s usage %s on %s (%d%%)", metricName, level.name().toLowerCase(), serviceId, usage);
        String body = String.format("<p>%s usage on node <b>%s</b> is <b>%d%%</b>, which is above the %s threshold of %d%%.</p>"
                        + "<p>Open Monitoring &rarr; Cluster in TBMQ to inspect the node.</p>",
                metricName, serviceId, usage, level.name().toLowerCase(),
                level == UsageLevel.CRITICAL ? settings.getCriticalUsage() : settings.getHighUsage());
        mailExecutor.execute(() -> recipients.forEach(email -> {
            try {
                mailService.sendEmail(email, subject, body);
            } catch (Exception e) {
                log.warn("[{}] Failed to send resource usage alert to {}", serviceId, email, e);
            }
        }));
    }

    private List<String> recipients(ResourceUsageAlertSettings settings) {
        if (StringUtils.isNotBlank(settings.getEmails())) {
            return Arrays.stream(settings.getEmails().split("[,;\\s]+")).filter(StringUtils::isNotBlank).toList();
        }
        return userService.findUsers(new PageLink(MAX_RECIPIENTS)).getData().stream()
                .map(User::getEmail)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    enum UsageLevel {
        NORMAL, HIGH, CRITICAL
    }

}
