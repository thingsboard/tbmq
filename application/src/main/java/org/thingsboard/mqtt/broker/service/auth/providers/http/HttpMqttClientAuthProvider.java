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
package org.thingsboard.mqtt.broker.service.auth.providers.http;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.List;
import java.util.regex.Pattern;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.PUB_SUB_AUTH_RULES_ALLOW_ALL;
import static org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType.HTTP;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpMqttClientAuthProvider implements MqttClientAuthProvider<HttpMqttAuthProviderConfiguration> {

    private final MqttAuthProviderService mqttAuthProviderService;
    private final AuthorizationRuleService authorizationRuleService;

    private volatile HttpMqttAuthProviderConfiguration configuration;
    private volatile AuthRulePatterns authRulePatterns;
    //    private volatile TbHttpClient httpClient;
    private volatile String httpClient;

    @SneakyThrows
    @PostConstruct
    public void init() {
        MqttAuthProvider httpAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.HTTP)
                .orElseThrow(() -> new IllegalStateException("Failed to initialize HTTP service authentication provider! Provider is missing in the DB!"));
        this.configuration = (HttpMqttAuthProviderConfiguration) httpAuthProvider.getConfiguration();
        this.authRulePatterns = authorizationRuleService.parseAuthorizationRule(configuration);

//        tbHttpClient = new TbHttpClient(config, context, metadataTemplate);
    }

    @PreDestroy
    public void destroy() {
        disable();
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        if (httpClient == null) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.HTTP);
        }
        log.trace("[{}] Authenticating client using HTTP service provider...", authContext.getClientId());
        byte[] passwordBytes = authContext.getPasswordBytes();
        try {
            return AuthResponse.success(ClientType.DEVICE, List.of(AuthRulePatterns.newInstance(List.of(Pattern.compile(PUB_SUB_AUTH_RULES_ALLOW_ALL)))), HTTP.name());
//            return httpClient.processMessage(authContext, new String(passwordBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[{}] Authentication failed", authContext.getClientId(), e);
            return AuthResponse.failure(e.getMessage());
        }
    }

    @SneakyThrows
    @Override
    public void onProviderUpdate(boolean enabled, HttpMqttAuthProviderConfiguration configuration) {
        this.configuration = configuration;
        this.authRulePatterns = authorizationRuleService.parseAuthorizationRule(configuration);
    }

    @Override
    public void enable() {

    }

    @Override
    public void disable() {
//        if (tbHttpClient != null) {
//            tbHttpClient.destroy();
//        }
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

//    @Override
//    public void doCheckConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {
//        try {
//            tbHttpClient = new TbHttpClient(getClientConfiguration(integration, HttpIntegrationConfig.class), ctx, null);
//            tbHttpClient.checkConnection();
//        } catch (Exception e) {
//            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
//        } finally {
//            if (tbHttpClient != null) {
//                tbHttpClient.destroy();
//            }
//        }
//    }
}
