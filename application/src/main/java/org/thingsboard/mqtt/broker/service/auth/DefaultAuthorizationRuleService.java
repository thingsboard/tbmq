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
package org.thingsboard.mqtt.broker.service.auth;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientTypeSslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SinglePubSubAuthRulesAware;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@Getter
public class DefaultAuthorizationRuleService implements AuthorizationRuleService {

    private final ConcurrentMap<String, ConcurrentMap<String, Boolean>> publishAuthMap = new ConcurrentHashMap<>();

    @Override
    public List<AuthRulePatterns> parseSslAuthorizationRule(ClientTypeSslMqttCredentials clientTypeSslMqttCredentials, String clientCommonName) throws AuthenticationException {
        SslMqttCredentials credentials = clientTypeSslMqttCredentials.getSslMqttCredentials();
        if (credentials == null) {
            throw new AuthenticationException("Cannot parse SslMqttCredentials");
        }

        List<AuthRulePatterns> authRulePatterns = credentials.getAuthRulesMapping().entrySet().stream()
                .filter(entry -> {
                    String certificateMatcherRegex = entry.getKey();
                    Pattern pattern = Pattern.compile(certificateMatcherRegex);
                    Matcher commonNameMatcher = pattern.matcher(clientCommonName);
                    return commonNameMatcher.find();
                })
                .map(Map.Entry::getValue)
                .map(this::newAuthRulePatterns)
                .collect(Collectors.toList());

        if (authRulePatterns.isEmpty()) {
            String errorMsg = String.format("Cannot find authorization rules for common name [%s] from credentials [%s]",
                    clientCommonName, clientTypeSslMqttCredentials.getName());
            log.warn(errorMsg);
            throw new AuthenticationException(errorMsg);
        }

        return authRulePatterns;
    }

    @Override
    public AuthRulePatterns parseAuthorizationRule(SinglePubSubAuthRulesAware credentials) throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException("Cannot parse SinglePubSubAuthRulesAware credentials!");
        }
        return newAuthRulePatterns(credentials.getAuthRules());
    }

    private AuthRulePatterns newAuthRulePatterns(PubSubAuthorizationRules pubSubAuthRules) {
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
        List<Pattern> patterns = stream.flatMap(List::stream).collect(Collectors.toList());
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
