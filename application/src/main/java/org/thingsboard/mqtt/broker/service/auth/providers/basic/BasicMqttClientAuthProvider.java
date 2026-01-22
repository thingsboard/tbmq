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
package org.thingsboard.mqtt.broker.service.auth.providers.basic;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicAuthResponse;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicMqttClientAuthProvider implements MqttClientAuthProvider<BasicMqttAuthProviderConfiguration> {

    private final AuthorizationRuleService authorizationRuleService;
    private final MqttClientCredentialsService clientCredentialsService;
    private final TbCacheOps cacheOps;
    private final MqttAuthProviderService mqttAuthProviderService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final HashFunction hashFunction = Hashing.sha256();

    private volatile boolean enabled;
    private volatile BasicMqttAuthProviderConfiguration configuration;

    @PostConstruct
    public void init() {
        MqttAuthProvider basicAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.MQTT_BASIC)
                .orElseThrow(() -> new IllegalStateException("Failed to initialize BASIC authentication provider! Provider is missing in the DB!"));
        this.enabled = basicAuthProvider.isEnabled();
        this.configuration = (BasicMqttAuthProviderConfiguration) basicAuthProvider.getConfiguration();
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        if (!enabled) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.MQTT_BASIC);
        }

        String clientId = authContext.getClientId();
        String username = authContext.getUsername();

        log.trace("[{}] Authenticating client with basic credentials", clientId);
        try {
            BasicAuthResponse basicAuthResponse = authWithBasicCredentials(clientId, username, authContext.getPasswordBytes());
            if (basicAuthResponse.isFailure()) {
                log.warn(basicAuthResponse.getErrorMsg());
                return AuthResponse.skip(basicAuthResponse.getErrorMsg());
            }
            MqttClientCredentials basicCredentials = basicAuthResponse.getCredentials();
            log.debug("[{}] Authenticated as {} with username {}", clientId, basicCredentials.getClientType(), username);
            BasicMqttCredentials credentials = JacksonUtil.fromString(basicCredentials.getCredentialsValue(), BasicMqttCredentials.class);
            AuthRulePatterns authRulePatterns = authorizationRuleService.parseAuthorizationRule(credentials);
            return AuthResponse.success(basicCredentials.getClientType(), Collections.singletonList(authRulePatterns), basicCredentials.getName());
        } catch (Exception e) {
            log.debug("[{}] Authentication failed", clientId, e);
            return AuthResponse.skip(e.getMessage());
        }
    }

    @Override
    public void onProviderUpdate(boolean enabled, BasicMqttAuthProviderConfiguration configuration) {
        this.enabled = enabled;
        this.configuration = configuration;
    }

    @Override
    public void enable() {
        enabled = true;
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private BasicAuthResponse authWithBasicCredentials(String clientId, String username, byte[] passwordBytes) {
        List<String> credentialIds = getCredentialIds(clientId, username);
        List<MqttClientCredentials> matchingCredentialsList = clientCredentialsService.findMatchingCredentials(credentialIds);
        if (matchingCredentialsList.isEmpty()) {
            return BasicAuthResponse.failure(formatErrorMsg(BasicAuthFailure.NO_CREDENTIALS_FOUND, clientId, username));
        }
        log.debug("Found credentials {} for credentialIds {}", matchingCredentialsList, credentialIds);
        String password = passwordBytesToString(passwordBytes);
        if (password != null) {
            var cached = cacheOps.lookup(BASIC_CREDENTIALS_PASSWORD_CACHE, toHashString(password), MqttClientCredentials.class);
            if (cached.status() == TbCacheOps.Status.HIT && matchingCredentialsList.contains(cached.value())) {
                return BasicAuthResponse.success(cached.value());
            }
        }

        for (MqttClientCredentials credentials : matchingCredentialsList) {
            BasicMqttCredentials basicMqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(credentials, BasicMqttCredentials.class);
            if (isMatchingPassword(password, basicMqttCredentials)) {
                if (password != null && basicMqttCredentials.getPassword() != null) {
                    cacheOps.put(BASIC_CREDENTIALS_PASSWORD_CACHE, toHashString(password), credentials);
                }
                return BasicAuthResponse.success(credentials);
            }
        }
        return BasicAuthResponse.failure(getBasicAuthPasswordErrorMsg(clientId, username, password));
    }

    private String getBasicAuthPasswordErrorMsg(String clientId, String username, String password) {
        return password != null ?
                formatErrorMsg(BasicAuthFailure.PASSWORD_NOT_MATCH, clientId, username) :
                formatErrorMsg(BasicAuthFailure.NO_PASSWORD_PROVIDED, clientId, username);
    }

    private String formatErrorMsg(BasicAuthFailure basicAuthFailure, String clientId, String username) {
        return String.format(basicAuthFailure.getErrorMsg(), clientId, username);
    }

    private List<String> getCredentialIds(String clientId, String username) {
        var strategy = configuration.getAuthStrategy();

        boolean hasClientId = !StringUtils.isEmpty(clientId);
        boolean hasUsername = !StringUtils.isEmpty(username);

        List<String> credentialIds = new ArrayList<>(3);
        switch (strategy) {
            case CLIENT_ID -> {
                if (hasClientId) {
                    credentialIds.add(ProtocolUtil.clientIdCredentialsId(clientId));
                }
            }
            case USERNAME -> {
                if (hasUsername) {
                    credentialIds.add(ProtocolUtil.usernameCredentialsId(username));
                }
            }
            case CLIENT_ID_AND_USERNAME -> {
                if (hasUsername) {
                    credentialIds.add(ProtocolUtil.usernameCredentialsId(username));
                }
                if (hasClientId) {
                    credentialIds.add(ProtocolUtil.clientIdCredentialsId(clientId));
                }
                if (hasUsername && hasClientId) {
                    credentialIds.add(ProtocolUtil.mixedCredentialsId(username, clientId));
                }
            }
        }
        return credentialIds;
    }

    private boolean isMatchingPassword(String password, BasicMqttCredentials basicMqttCredentials) {
        return basicMqttCredentials.getPassword() == null
                || (password != null && passwordEncoder.matches(password, basicMqttCredentials.getPassword()));
    }

    private String passwordBytesToString(byte[] passwordBytes) {
        return passwordBytes != null ? new String(passwordBytes, StandardCharsets.UTF_8) : null;
    }

    private String toHashString(String rawPassword) {
        return hashFunction.newHasher().putString(rawPassword, StandardCharsets.UTF_8).hash().toString();
    }
}
