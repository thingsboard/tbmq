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
package org.thingsboard.mqtt.broker.service.limits;

import io.netty.handler.codec.mqtt.MqttMessage;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.TbRateLimits;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.IncomingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.OutgoingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private final IncomingRateLimitsConfiguration incomingRateLimitsConfiguration;
    private final OutgoingRateLimitsConfiguration outgoingRateLimitsConfiguration;
    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;
    private final DevicePersistedMsgsRateLimitsConfiguration devicePersistedMsgsRateLimitsConfiguration;
    private final ClientSessionService clientSessionService;
    private final RateLimitCacheService rateLimitCacheService;

    @Getter
    private ConcurrentMap<String, TbRateLimits> incomingPublishClientLimits;
    @Getter
    private ConcurrentMap<String, TbRateLimits> outgoingPublishClientLimits;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;
    @Value("${mqtt.application-clients-limit:0}")
    @Setter
    private int applicationClientsLimit;

    @PostConstruct
    public void init() {
        if (incomingRateLimitsConfiguration.isEnabled()) {
            incomingPublishClientLimits = new ConcurrentHashMap<>();
        }
        if (outgoingRateLimitsConfiguration.isEnabled()) {
            outgoingPublishClientLimits = new ConcurrentHashMap<>();
        }
    }

    @Override
    public boolean checkIncomingLimits(String clientId, UUID sessionId, MqttMessage msg) {
        if (!incomingRateLimitsConfiguration.isEnabled()) {
            return true;
        }
        TbRateLimits rateLimits = incomingPublishClientLimits.computeIfAbsent(clientId, id -> new TbRateLimits(incomingRateLimitsConfiguration.getClientConfig()));
        if (!rateLimits.tryConsume()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Client level incoming PUBLISH rate limit detected: {}", clientId, sessionId, msg);
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean checkOutgoingLimits(String clientId, PublishMsgProto msg) {
        if (!outgoingRateLimitsConfiguration.isEnabled()) {
            return true;
        }
        if (msg.getQos() != 0) {
            return true;
        }
        TbRateLimits rateLimits = outgoingPublishClientLimits.computeIfAbsent(clientId, id -> new TbRateLimits(outgoingRateLimitsConfiguration.getClientConfig()));
        if (!rateLimits.tryConsume()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Client level outgoing PUBLISH rate limit detected: {}", clientId, msg);
            }
            return false;
        }
        return true;
    }

    @Override
    public void remove(String clientId) {
        if (clientId != null) {
            if (incomingPublishClientLimits != null) {
                incomingPublishClientLimits.remove(clientId);
            }
            if (outgoingPublishClientLimits != null) {
                outgoingPublishClientLimits.remove(clientId);
            }
        }
    }

    @Override
    public boolean checkSessionsLimit(String clientId) {
        if (sessionsLimit <= 0) {
            return true;
        }

        ClientSessionInfo clientSessionInfo = clientSessionService.getClientSessionInfo(clientId);
        if (clientSessionInfo != null) {
            return true;
        }

        long newSessionCount = rateLimitCacheService.incrementSessionCount();

        if (newSessionCount > sessionsLimit) {
            log.trace("Client sessions count limit detected! Allowed: {} sessions", sessionsLimit);
            rateLimitCacheService.decrementSessionCount();
            return false;
        }

        return true;
    }

    @Override
    public boolean checkIntegrationsLimit() {
        if (applicationClientsLimit <= 0) {
            return true;
        }

        long newAppClientsCount = rateLimitCacheService.incrementApplicationClientsCount();

        if (newAppClientsCount > applicationClientsLimit) {
            log.trace("Integrations count limit detected! Allowed: {} Integrations", applicationClientsLimit);
            rateLimitCacheService.decrementApplicationClientsCount();
            return false;
        }

        return true;
    }

    @Override
    public boolean checkApplicationClientsLimit(SessionInfo sessionInfo) {
        if (applicationClientsLimit <= 0) {
            return true;
        }
        if (sessionInfo.isPersistentAppClient()) {

            ClientSessionInfo clientSessionInfo = clientSessionService.getClientSessionInfo(sessionInfo.getClientId());
            if (clientSessionInfo != null && clientSessionInfo.isPersistentAppClient()) {
                return true;
            }

            long newAppClientsCount = rateLimitCacheService.incrementApplicationClientsCount();

            if (newAppClientsCount > applicationClientsLimit) {
                log.trace("Application clients count limit detected! Allowed: {} App clients", applicationClientsLimit);
                rateLimitCacheService.decrementApplicationClientsCount();
                return false;
            }

        }
        return true;
    }

    @Override
    public boolean checkDevicePersistedMsgsLimit() {
        if (!devicePersistedMsgsRateLimitsConfiguration.isEnabled()) {
            return true;
        }
        if (!rateLimitCacheService.tryConsumeDevicePersistedMsg()) {
            if (log.isTraceEnabled()) {
                log.trace("Device persisted messages rate limit detected!");
            }
            return false;
        }
        return true;
    }

    @Override
    public long tryConsumeAsMuchAsPossibleDevicePersistedMsgs(long limit) {
        return rateLimitCacheService.tryConsumeAsMuchAsPossibleDevicePersistedMsgs(limit);
    }

    @Override
    public boolean isDevicePersistedMsgsLimitEnabled() {
        return devicePersistedMsgsRateLimitsConfiguration.isEnabled();
    }

    @Override
    public boolean checkTotalMsgsLimit() {
        if (!isTotalMsgsLimitEnabled()) {
            return true;
        }
        if (!rateLimitCacheService.tryConsumeTotalMsg()) {
            if (log.isTraceEnabled()) {
                log.trace("Total incoming and outgoing messages rate limit detected!");
            }
            return false;
        }
        return true;
    }

    @Override
    public long tryConsumeAsMuchAsPossibleTotalMsgs(long limit) {
        return rateLimitCacheService.tryConsumeAsMuchAsPossibleTotalMsgs(limit);
    }

    @Override
    public boolean isTotalMsgsLimitEnabled() {
        return totalMsgsRateLimitsConfiguration.isEnabled();
    }
}
