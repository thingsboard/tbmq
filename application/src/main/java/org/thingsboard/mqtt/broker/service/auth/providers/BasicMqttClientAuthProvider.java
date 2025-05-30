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

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicAuthResponse;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicMqttClientAuthProvider implements MqttClientAuthProvider {

    private final AuthorizationRuleService authorizationRuleService;
    private final MqttClientCredentialsService clientCredentialsService;
    private final CacheNameResolver cacheNameResolver;
    private BCryptPasswordEncoder passwordEncoder;
    private HashFunction hashFunction;

    @Autowired
    public BasicMqttClientAuthProvider(AuthorizationRuleService authorizationRuleService,
                                       MqttClientCredentialsService clientCredentialsService,
                                       CacheNameResolver cacheNameResolver,
                                       @Lazy BCryptPasswordEncoder passwordEncoder) {
        this.authorizationRuleService = authorizationRuleService;
        this.clientCredentialsService = clientCredentialsService;
        this.cacheNameResolver = cacheNameResolver;
        this.passwordEncoder = passwordEncoder;
        this.hashFunction = Hashing.sha256();
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client with basic credentials", authContext.getClientId());
        }
        BasicAuthResponse basicAuthResponse = authWithBasicCredentials(authContext.getClientId(), authContext.getUsername(), authContext.getPasswordBytes());
        if (basicAuthResponse.isFailure()) {
            log.warn(basicAuthResponse.getErrorMsg());
            return new AuthResponse(false, null, null, basicAuthResponse.getErrorMsg());
        }
        MqttClientCredentials basicCredentials = basicAuthResponse.getCredentials();
        putIntoClientSessionCredsCache(authContext, basicCredentials);
        if (log.isDebugEnabled()) {
            log.debug("[{}] Authenticated as {} with username {}", authContext.getClientId(), basicCredentials.getClientType(), authContext.getUsername());
        }
        BasicMqttCredentials credentials = JacksonUtil.fromString(basicCredentials.getCredentialsValue(), BasicMqttCredentials.class);
        AuthRulePatterns authRulePatterns = authorizationRuleService.parseAuthorizationRule(credentials);
        return new AuthResponse(true, basicCredentials.getClientType(), Collections.singletonList(authRulePatterns));
    }

    @Override
    public void onConfigurationUpdate(String configuration) {
        // Configuration for Basic provider is static so no logic here now.
    }

    private BasicAuthResponse authWithBasicCredentials(String clientId, String username, byte[] passwordBytes) {
        List<String> credentialIds = getCredentialIds(clientId, username);
        List<MqttClientCredentials> matchingCredentialsList = clientCredentialsService.findMatchingCredentials(credentialIds);
        if (matchingCredentialsList.isEmpty()) {
            return BasicAuthResponse.failure(formatErrorMsg(BasicAuthFailure.NO_CREDENTIALS_FOUND, clientId, username));
        }
        if (log.isDebugEnabled()) {
            log.debug("Found credentials {} for credentialIds {}", matchingCredentialsList, credentialIds);
        }
        String password = passwordBytesToString(passwordBytes);
        if (password != null) {
            MqttClientCredentials credentialsFromCache = getBasicCredsPwCache().get(toHashString(password), MqttClientCredentials.class);
            if (credentialsFromCache != null && matchingCredentialsList.contains(credentialsFromCache)) {
                return BasicAuthResponse.success(credentialsFromCache);
            }
        }

        for (MqttClientCredentials credentials : matchingCredentialsList) {
            BasicMqttCredentials basicMqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(credentials, BasicMqttCredentials.class);
            if (isMatchingPassword(password, basicMqttCredentials)) {
                if (password != null && basicMqttCredentials.getPassword() != null) {
                    getBasicCredsPwCache().put(toHashString(password), credentials);
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
        List<String> credentialIds = new ArrayList<>();
        if (!StringUtils.isEmpty(username)) {
            credentialIds.add(ProtocolUtil.usernameCredentialsId(username));
        }
        if (!StringUtils.isEmpty(clientId)) {
            credentialIds.add(ProtocolUtil.clientIdCredentialsId(clientId));
        }
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(clientId)) {
            credentialIds.add(ProtocolUtil.mixedCredentialsId(username, clientId));
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

    private Cache getBasicCredsPwCache() {
        return cacheNameResolver.getCache(CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE);
    }

    private void putIntoClientSessionCredsCache(AuthContext authContext, MqttClientCredentials basicCredentials) {
        getClientSessionCredentialsCache().put(authContext.getClientId(), basicCredentials.getName());
    }

    private Cache getClientSessionCredentialsCache() {
        return cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE);
    }

    private String toHashString(String rawPassword) {
        return hashFunction.newHasher().putString(rawPassword, StandardCharsets.UTF_8).hash().toString();
    }
}
