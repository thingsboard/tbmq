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
package org.thingsboard.mqtt.broker.standalone.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.service.auth.ClientTypePolicy;

@Slf4j
@Component
@StandaloneComponent
public class StandaloneClientTypePolicy implements ClientTypePolicy {

    private final DefaultCounter applicationClientsMappedToDevice;

    public StandaloneClientTypePolicy(StatsFactory statsFactory) {
        this.applicationClientsMappedToDevice = statsFactory.createDefaultCounter(StatsType.APPLICATION_CLIENTS_MAPPED_TO_DEVICE.getPrintName());
    }

    @Override
    public ClientType resolve(String clientId, ClientType authenticated) {
        ClientType resolved = map(authenticated);
        if (resolved != authenticated) {
            log.warn("[{}] APPLICATION clients are not supported in TBMQ Lite; connecting as DEVICE", clientId);
            applicationClientsMappedToDevice.increment();
        }
        return resolved;
    }

    @Override
    public ClientType resolveOnReAuth(String clientId, ClientType authenticated) {
        return map(authenticated);
    }

    // the standalone edition has no APPLICATION persistence
    private static ClientType map(ClientType authenticated) {
        return authenticated == ClientType.APPLICATION ? ClientType.DEVICE : authenticated;
    }

}
