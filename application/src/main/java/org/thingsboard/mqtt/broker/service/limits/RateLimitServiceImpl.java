/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.util.TbRateLimits;
import org.thingsboard.mqtt.broker.config.IncomingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.OutgoingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import javax.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private final IncomingRateLimitsConfiguration incomingRateLimitsConfiguration;
    private final OutgoingRateLimitsConfiguration outgoingRateLimitsConfiguration;
    private final ClientSessionService clientSessionService;

    @Getter
    private ConcurrentMap<String, TbRateLimits> incomingPublishClientLimits;
    @Getter
    private ConcurrentMap<String, TbRateLimits> outgoingPublishClientLimits;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;

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
    public boolean checkOutgoingLimits(String clientId, QueueProtos.PublishMsgProto msg) {
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
            incomingPublishClientLimits.remove(clientId);
            outgoingPublishClientLimits.remove(clientId);
        }
    }

    @Override
    public boolean checkSessionsLimit(String clientId) {
        if (sessionsLimit <= 0) {
            return true;
        }
        int clientSessionsCount = clientSessionService.getClientSessionsCount();
        if (clientSessionsCount >= sessionsLimit) {
            if (log.isTraceEnabled()) {
                log.trace("Client sessions count limit detected! Allowed: [{}], current count: [{}]", sessionsLimit, clientSessionsCount);
            }
            ClientSessionInfo clientSessionInfo = clientSessionService.getClientSessionInfo(clientId);
            return clientSessionInfo != null;
        }
        return true;
    }

}
