/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.limits;

import io.netty.handler.codec.mqtt.MqttMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.TbRateLimits;
import org.thingsboard.mqtt.broker.config.RateLimitsConfiguration;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private final RateLimitsConfiguration rateLimitsConfiguration;
    private final ConcurrentMap<String, TbRateLimits> clientLimits = new ConcurrentHashMap<>();

    @Override
    public boolean checkLimits(String clientId, UUID sessionId, MqttMessage msg) {
        if (!rateLimitsConfiguration.isEnabled()) {
            return true;
        }
        TbRateLimits rateLimits = clientLimits.computeIfAbsent(clientId, id -> new TbRateLimits(rateLimitsConfiguration.getClientConfig()));
        if (!rateLimits.tryConsume()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Client level rate limit detected: {}", clientId, sessionId, msg);
            }
            return false;
        }
        return true;
    }

    @Override
    public void remove(String clientId) {
        if (clientId != null) {
            clientLimits.remove(clientId);
        }
    }
}
