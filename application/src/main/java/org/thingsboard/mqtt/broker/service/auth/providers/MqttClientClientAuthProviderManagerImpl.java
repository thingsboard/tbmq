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
package org.thingsboard.mqtt.broker.service.auth.providers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttClientClientAuthProviderManagerImpl implements MqttClientAuthProviderManager {

    @Value("${security.mqtt.basic.enabled}")
    private boolean basicAuthEnabled;
    @Value("${security.mqtt.ssl.enabled}")
    private boolean sslAuthEnabled;

    private final BasicMqttClientAuthProvider basicMqttClientAuthProvider;
    private final SslMqttClientAuthProvider sslMqttClientAuthProvider;

    private Map<MqttAuthProviderType, MqttClientAuthProvider> authProviders;

    @PostConstruct
    public void init() {
        Map<MqttAuthProviderType, MqttClientAuthProvider> tmpProvidersMap = new HashMap<>();

        if (basicAuthEnabled) {
            tmpProvidersMap.put(MqttAuthProviderType.BASIC, basicMqttClientAuthProvider);
        }
        if (sslAuthEnabled) {
            tmpProvidersMap.put(MqttAuthProviderType.X_509, sslMqttClientAuthProvider);
        }

        this.authProviders = Collections.unmodifiableMap(tmpProvidersMap);
    }

    @Override
    public Map<MqttAuthProviderType, MqttClientAuthProvider> getActiveAuthProviders() {
        return authProviders;
    }

    @Override
    public void handleProviderNotification(MqttAuthProviderProto notification) {

    }
}