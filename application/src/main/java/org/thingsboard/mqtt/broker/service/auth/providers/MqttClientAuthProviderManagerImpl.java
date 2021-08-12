/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttClientAuthProviderManagerImpl implements MqttClientAuthProviderManager {
    @Value("${security.mqtt.basic.enabled}")
    private Boolean basicAuthEnabled;
    @Value("${security.mqtt.ssl.enabled}")
    private Boolean sslAuthEnabled;

    private final BasicMqttClientAuthProvider basicMqttClientAuthProvider;
    private final SslMqttClientAuthProvider sslMqttClientAuthProvider;

    private List<MqttClientAuthProvider> authProviders;

    @PostConstruct
    public void init() {
        List<MqttClientAuthProvider> tmpProvidersList = new ArrayList<>();

        if (basicAuthEnabled) {
            tmpProvidersList.add(basicMqttClientAuthProvider);
        }
        if (sslAuthEnabled) {
            tmpProvidersList.add(sslMqttClientAuthProvider);
        }

        this.authProviders = Collections.unmodifiableList(tmpProvidersList);
    }

    @Override
    public List<MqttClientAuthProvider> getActiveAuthProviders() {
        return authProviders;
    }
}
