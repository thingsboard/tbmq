/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DefaultAuthorizationRuleService implements AuthorizationRuleService {
    @Override
    public List<AuthorizationRule> parseSslAuthorizationRule(SslMqttCredentials credentials, String clientCommonName) throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException("Cannot parse SslMqttCredentials.");
        }

        List<AuthorizationRule> authorizationRules = credentials.getAuthorizationRulesMapping().entrySet().stream()
                .filter(entry -> {
                    String certificateMatcherRegex = entry.getKey();
                    Pattern pattern = Pattern.compile(certificateMatcherRegex);
                    Matcher commonNameMatcher = pattern.matcher(clientCommonName);
                    return commonNameMatcher.find();
                })
                .map(Map.Entry::getValue)
                .map(topicRules -> new AuthorizationRule(compilePatterns(topicRules)))
                .collect(Collectors.toList());

        if (authorizationRules.isEmpty()) {
            throw new AuthenticationException("Cannot find authorization rules for common name");
        }

        return authorizationRules;
    }

    @Override
    public AuthorizationRule parseBasicAuthorizationRule(BasicMqttCredentials credentials) throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException("Cannot parse BasicMqttCredentials.");
        }
        if (CollectionUtils.isEmpty(credentials.getAuthorizationRulePatterns())) {
            return null;
        } else {
            List<Pattern> patterns = compilePatterns(credentials.getAuthorizationRulePatterns());
            return new AuthorizationRule(patterns);
        }
    }

    private List<Pattern> compilePatterns(List<String> authorizationRulePatterns) {
        return authorizationRulePatterns.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    @Override
    public boolean isAuthorized(String topic, List<AuthorizationRule> authorizationRules) {
        return authorizationRules.stream()
                .map(AuthorizationRule::getPatterns)
                .anyMatch(patterns -> patterns.stream()
                        .map(pattern -> pattern.matcher(topic))
                        .anyMatch(Matcher::matches));
    }
}
