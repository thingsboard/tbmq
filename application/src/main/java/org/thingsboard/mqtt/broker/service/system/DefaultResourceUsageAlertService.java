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
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.audit.AuditLogService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.mail.MailService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultResourceUsageAlertService implements ResourceUsageAlertService {

    static final String SYSTEM_USER_NAME = "system";
    static final int RECOVERY_MARGIN = 5;
    static final long SETTINGS_CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30);
    private static final int USERS_PAGE_SIZE = 100;

    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final UserService userService;

    // Last band seen per node and metric, so an alert fires once per crossing instead of on every sample.
    private final ConcurrentMap<LevelKey, UsageLevel> levels = new ConcurrentHashMap<>();
    private volatile CachedSettings cachedSettings;
    long settingsCacheTtlMs = SETTINGS_CACHE_TTL_MS;

    @Override
    public void evaluate(String serviceId, String serviceType, Map<String, Long> usagePercentByMetric) {
        if (usagePercentByMetric == null || usagePercentByMetric.isEmpty()) {
            return;
        }
        ResourceUsageAlertSettings settings = currentSettings();
        usagePercentByMetric.forEach((metric, usage) -> {
            LevelKey key = new LevelKey(serviceType, serviceId, metric);
            UsageLevel previous = levels.getOrDefault(key, UsageLevel.NORMAL);
            UsageLevel level = nextLevel(previous, usage, settings);
            if (level == previous) {
                return;
            }
            if (level == UsageLevel.NORMAL) {
                levels.remove(key);
                audit(ActionType.RESOURCE_USAGE_RECOVERED, serviceId, serviceType, metric, usage, level, settings);
                return;
            }
            levels.put(key, level);
            if (level.ordinal() < previous.ordinal()) {
                return;
            }
            audit(ActionType.RESOURCE_USAGE_ALERT, serviceId, serviceType, metric, usage, level, settings);
            if (settings.isEmailEnabled()) {
                notifyByEmail(serviceId, serviceType, metric, usage, level, settings);
            }
        });
    }

    @Override
    public AdminSettings getAdminSettings() {
        AdminSettings stored = findStoredSettings();
        AdminSettings adminSettings = stored != null ? stored : new AdminSettings();
        adminSettings.setKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        adminSettings.setJsonValue(JacksonUtil.valueToTree(toSettings(stored)));
        return adminSettings;
    }

    @Override
    public void onSettingsUpdate() {
        cachedSettings = null;
    }

    ResourceUsageAlertSettings currentSettings() {
        CachedSettings cached = cachedSettings;
        long now = System.currentTimeMillis();
        if (cached == null || now - cached.loadedAt() >= settingsCacheTtlMs) {
            cached = new CachedSettings(toSettings(findStoredSettings()), now);
            cachedSettings = cached;
        }
        return cached.settings();
    }

    private AdminSettings findStoredSettings() {
        return adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
    }

    private static ResourceUsageAlertSettings toSettings(AdminSettings stored) {
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

    /**
     * Rising into a band takes usage above its threshold; leaving a band takes usage at or below its threshold minus
     * {@link #RECOVERY_MARGIN}, so a node hovering around a threshold does not flap between bands on every sample.
     */
    static UsageLevel nextLevel(UsageLevel previous, long usage, ResourceUsageAlertSettings settings) {
        if (usage > settings.getCriticalUsage()
                || (previous == UsageLevel.CRITICAL && usage > settings.getCriticalUsage() - RECOVERY_MARGIN)) {
            return UsageLevel.CRITICAL;
        }
        if (usage > settings.getHighUsage()
                || (previous != UsageLevel.NORMAL && usage > settings.getHighUsage() - RECOVERY_MARGIN)) {
            return UsageLevel.HIGH;
        }
        return UsageLevel.NORMAL;
    }

    private void audit(ActionType actionType, String serviceId, String serviceType, String metric, long usage,
                       UsageLevel level, ResourceUsageAlertSettings settings) {
        // logEntityAction needs a real user; these entries are written by the system itself.
        AuditLog auditLog = new AuditLog(UUID.randomUUID());
        auditLog.setCreatedTime(System.currentTimeMillis());
        auditLog.setEntityId(ServiceInfoIds.auditEntityId(serviceId, serviceType));
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

    private void notifyByEmail(String serviceId, String serviceType, String metric, long usage, UsageLevel level,
                               ResourceUsageAlertSettings settings) {
        List<String> recipients = recipients(settings);
        if (recipients.isEmpty()) {
            log.debug("[{}] No recipients for resource usage alert", serviceId);
            return;
        }
        int threshold = level == UsageLevel.CRITICAL ? settings.getCriticalUsage() : settings.getHighUsage();
        ResourceUsageAlert alert = new ResourceUsageAlert(serviceId, serviceType, metric, usage, level.name(), threshold);
        mailService.sendResourceUsageAlertEmailAsync(alert, recipients);
    }

    private List<String> recipients(ResourceUsageAlertSettings settings) {
        List<String> emails = settings.emailList();
        if (!emails.isEmpty()) {
            return emails;
        }
        List<String> userEmails = new ArrayList<>();
        PageLink pageLink = new PageLink(USERS_PAGE_SIZE);
        PageData<User> users;
        do {
            users = userService.findUsers(pageLink);
            users.getData().stream()
                    .map(User::getEmail)
                    .filter(StringUtils::isNotBlank)
                    .forEach(userEmails::add);
            pageLink = pageLink.nextPageLink();
        } while (users.hasNext());
        return userEmails;
    }

    enum UsageLevel {
        NORMAL, HIGH, CRITICAL
    }

    record LevelKey(String serviceType, String serviceId, String metric) {
    }

    private record CachedSettings(ResourceUsageAlertSettings settings, long loadedAt) {
    }

}
