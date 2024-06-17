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
package org.thingsboard.mqtt.broker.common.data.ws;

import jakarta.validation.Valid;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Data
public class WebSocketConnectionConfiguration {

    @NoXss
    private String url;

    private boolean rejectUnauthorized;

    private UUID clientCredentialsId;
    @NoXss
    private String clientId;
    @NoXss
    private String username;
    private boolean passwordRequired;

    private boolean cleanStart;
    private int keepAlive;
    private TimeUnit keepAliveUnit;
    private int connectTimeout;
    private TimeUnit connectTimeoutUnit;
    private int reconnectPeriod;
    private TimeUnit reconnectPeriodUnit;
    private int mqttVersion;
    private long sessionExpiryInterval;
    private TimeUnit sessionExpiryIntervalUnit;
    private int maxPacketSize;
    private SizeUnit maxPacketSizeUnit;
    private int topicAliasMax;
    private int receiveMax;
    private boolean requestResponseInfo;
    private boolean requestProblemInfo;

    @Valid
    private LastWillMsg lastWillMsg;

    @Valid
    private UserProperties userProperties;

    public WebSocketConnectionConfiguration() {
    }

    public WebSocketConnectionConfiguration(String clientId) {
        this.clientId = clientId;
    }
}
