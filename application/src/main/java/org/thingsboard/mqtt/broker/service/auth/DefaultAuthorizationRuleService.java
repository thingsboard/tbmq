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
package org.thingsboard.mqtt.broker.service.auth;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientTypeSslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SinglePubSubAuthRulesAware;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.util.AuthRulesUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.thingsboard.mqtt.broker.service.auth.providers.basic.BasicAuthFailure.CAN_NOT_PARSE_PUB_SUB_RULES;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.CAN_NOT_PARSE_SSL_CREDS;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.NO_AUTH_RULES_FOR_CN_IN_CREDS;

@Service
@Slf4j
@Getter
public class DefaultAuthorizationRuleService implements AuthorizationRuleService {

    static final String INVALID_CLIENT_ID_PLACEHOLDER_ERROR =
            "Client ID contains characters not allowed with the ${clientId} placeholder";

    private final ConcurrentMap<String, ConcurrentMap<String, Boolean>> publishAuthMap = new ConcurrentHashMap<>();

    @Override
    public List<AuthRulePatterns> parseSslAuthorizationRule(ClientTypeSslMqttCredentials clientTypeSslMqttCredentials,
                                                            String clientCommonName, String clientId) throws AuthenticationException {
        SslMqttCredentials credentials = clientTypeSslMqttCredentials.getSslMqttCredentials();
        if (credentials == null) {
            throw new AuthenticationException(CAN_NOT_PARSE_SSL_CREDS.getErrorMsg());
        }

        List<AuthRulePatterns> authRulePatterns = new ArrayList<>();
        for (Map.Entry<String, PubSubAuthorizationRules> entry : credentials.getAuthRulesMapping().entrySet()) {
            Pattern certificateMatcherPattern = Pattern.compile(entry.getKey());
            if (certificateMatcherPattern.matcher(clientCommonName).find()) {
                authRulePatterns.add(newAuthRulePatterns(entry.getValue(), clientCommonName, clientId));
            }
        }

        if (authRulePatterns.isEmpty()) {
            String errorMsg = String.format(NO_AUTH_RULES_FOR_CN_IN_CREDS.getErrorMsg(),
                    clientCommonName, clientTypeSslMqttCredentials.getName());
            log.warn(errorMsg);
            throw new AuthenticationException(errorMsg);
        }

        return authRulePatterns;
    }

    @Override
    public AuthRulePatterns parseAuthorizationRule(SinglePubSubAuthRulesAware credentials) throws AuthenticationException {
        return parseAuthorizationRule(credentials, null);
    }

    @Override
    public AuthRulePatterns parseAuthorizationRule(SinglePubSubAuthRulesAware credentials, String clientId) throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException(CAN_NOT_PARSE_PUB_SUB_RULES.getErrorMsg());
        }
        return newAuthRulePatterns(credentials.getAuthRules(), null, clientId);
    }

    private AuthRulePatterns newAuthRulePatterns(PubSubAuthorizationRules pubSubAuthRules, String clientCommonName,
                                                 String clientId) throws AuthenticationException {
        validateClientIdPlaceholder(pubSubAuthRules, clientId);
        return new AuthRulePatterns(
                applyPlaceholderAndCompilePatterns(pubSubAuthRules.getPubAuthRulePatterns(), clientCommonName, clientId),
                applyPlaceholderAndCompilePatterns(pubSubAuthRules.getSubAuthRulePatterns(), clientCommonName, clientId));
    }

    @Override
    public AuthRulePatterns parseAuthorizationRule(PubSubAuthorizationRules authRules, String clientCommonName,
                                                   String clientId) throws AuthenticationException {
        if (authRules == null) {
            throw new AuthenticationException(CAN_NOT_PARSE_PUB_SUB_RULES.getErrorMsg());
        }
        return newAuthRulePatterns(authRules, clientCommonName, clientId);
    }

    private void validateClientIdPlaceholder(PubSubAuthorizationRules authRules, String clientId) throws AuthenticationException {
        if (clientId == null || !containsMqttTopicSyntax(clientId)) {
            return;
        }
        if (containsClientIdPlaceholder(authRules.getPubAuthRulePatterns()) ||
                containsClientIdPlaceholder(authRules.getSubAuthRulePatterns())) {
            throw new AuthenticationException(INVALID_CLIENT_ID_PLACEHOLDER_ERROR);
        }
    }

    private boolean containsClientIdPlaceholder(List<String> patterns) {
        return !CollectionUtils.isEmpty(patterns) && patterns.stream()
                .anyMatch(pattern -> pattern.contains(AuthRulesUtil.CLIENT_ID_PLACEHOLDER));
    }

    private boolean containsMqttTopicSyntax(String clientId) {
        return clientId.indexOf('+') >= 0 || clientId.indexOf('#') >= 0 || clientId.indexOf('/') >= 0;
    }

    private List<Pattern> applyPlaceholderAndCompilePatterns(List<String> authRulePatterns, String clientCommonName, String clientId) {
        return CollectionUtils.isEmpty(authRulePatterns) ? Collections.emptyList() :
                authRulePatterns
                        .stream()
                        .map(pattern -> AuthRulesUtil.processPattern(pattern, clientCommonName, clientId))
                        .map(Pattern::compile)
                        .collect(Collectors.toList());
    }

    @Override
    public AuthRulePatterns parsePubSubAuthorizationRule(PubSubAuthorizationRules pubSubAuthRules) {
        return new AuthRulePatterns(
                compilePatterns(pubSubAuthRules.getPubAuthRulePatterns()),
                compilePatterns(pubSubAuthRules.getSubAuthRulePatterns()));
    }

    private List<Pattern> compilePatterns(List<String> authRulePatterns) {
        return CollectionUtils.isEmpty(authRulePatterns) ? Collections.emptyList() :
                authRulePatterns.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    @Override
    public boolean isPubAuthorized(String clientId, String topic, List<AuthRulePatterns> authRulePatterns) {
        if (CollectionUtils.isEmpty(authRulePatterns)) {
            return true;
        }
        ConcurrentMap<String, Boolean> topicAuthMap = publishAuthMap.get(clientId);
        if (topicAuthMap == null) {
            topicAuthMap = publishAuthMap.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>());
        }
        Boolean isAuthorized = topicAuthMap.get(topic);
        if (isAuthorized == null) {
            return topicAuthMap.computeIfAbsent(topic, s -> {
                Stream<List<Pattern>> pubPatterns = authRulePatterns.stream().map(AuthRulePatterns::getPubPatterns);
                return isAuthorized(topic, pubPatterns);
            });
        }
        return isAuthorized;
    }

    @Override
    public boolean isSubAuthorized(String topic, List<AuthRulePatterns> authRulePatterns) {
        Stream<List<Pattern>> subPatterns = authRulePatterns.stream().map(AuthRulePatterns::getSubPatterns);
        return isAuthorized(topic, subPatterns);
    }

    private boolean isAuthorized(String topic, Stream<List<Pattern>> stream) {
        List<Pattern> patterns = stream.flatMap(List::stream).toList();
        if (CollectionUtils.isEmpty(patterns)) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pattern.matcher(topic).matches());
    }

    @Override
    public void evict(String clientId) {
        if (clientId != null) {
            var topicAuthMap = publishAuthMap.remove(clientId);
            if (topicAuthMap != null) {
                topicAuthMap.clear();
            }
        }
    }
}
