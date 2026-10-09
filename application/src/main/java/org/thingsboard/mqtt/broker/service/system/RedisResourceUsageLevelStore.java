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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.RESOURCE_USAGE_LEVELS_KEY;

@Component
@RequiredArgsConstructor
public class RedisResourceUsageLevelStore implements ResourceUsageLevelStore {

    private static final String METRIC_SEPARATOR = "|";

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    private String levelsKey;

    @PostConstruct
    public void init() {
        levelsKey = cacheProperties.prefixKey(RESOURCE_USAGE_LEVELS_KEY);
    }

    @Override
    public UsageLevel get(LevelKey key) {
        Object stored = redisTemplate.opsForHash().get(levelsKey, field(key));
        return stored == null ? UsageLevel.NORMAL : UsageLevel.valueOf(String.valueOf(stored));
    }

    @Override
    public void put(LevelKey key, UsageLevel level) {
        redisTemplate.opsForHash().put(levelsKey, field(key), level.name());
    }

    @Override
    public void remove(LevelKey key) {
        redisTemplate.opsForHash().delete(levelsKey, field(key));
    }

    @Override
    public void removeAll(ServiceType serviceType, String serviceId) {
        String prefix = fieldPrefix(serviceType, serviceId);
        Object[] fields = redisTemplate.opsForHash().keys(levelsKey).stream()
                .filter(field -> String.valueOf(field).startsWith(prefix))
                .toArray();
        if (fields.length > 0) {
            redisTemplate.opsForHash().delete(levelsKey, fields);
        }
    }

    private static String field(LevelKey key) {
        return fieldPrefix(key.serviceType(), key.serviceId()) + key.metric();
    }

    private static String fieldPrefix(ServiceType serviceType, String serviceId) {
        return ServiceInfoIds.registryField(serviceId, serviceType.name()) + METRIC_SEPARATOR;
    }

}
