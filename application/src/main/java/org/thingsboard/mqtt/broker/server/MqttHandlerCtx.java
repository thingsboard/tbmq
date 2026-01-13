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
package org.thingsboard.mqtt.broker.server;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.security.ssl.MqttClientAuthType;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitBatchProcessor;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

@Component
@Data
public class MqttHandlerCtx {

    private final ClientMqttActorManager actorManager;
    private final ClientLogger clientLogger;
    private final RateLimitService rateLimitService;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final RateLimitBatchProcessor rateLimitBatchProcessor;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Value("${mqtt.max-in-flight-msgs:1000}")
    private int maxInFlightMsgs;

    private volatile MqttClientAuthType clientAuthType = MqttClientAuthType.CLIENT_AUTH_REQUESTED;

    @Autowired
    public MqttHandlerCtx(ClientMqttActorManager actorManager,
                          ClientLogger clientLogger,
                          RateLimitService rateLimitService,
                          MqttMessageGenerator mqttMessageGenerator,
                          @Autowired(required = false) RateLimitBatchProcessor rateLimitBatchProcessor,
                          TbMessageStatsReportClient tbMessageStatsReportClient) {
        this.actorManager = actorManager;
        this.clientLogger = clientLogger;
        this.rateLimitService = rateLimitService;
        this.mqttMessageGenerator = mqttMessageGenerator;
        this.rateLimitBatchProcessor = rateLimitBatchProcessor;
        this.tbMessageStatsReportClient = tbMessageStatsReportClient;
    }
}
