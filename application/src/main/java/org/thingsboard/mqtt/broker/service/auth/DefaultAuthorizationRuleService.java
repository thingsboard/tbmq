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
package org.thingsboard.mqtt.broker.service.auth;

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.exception.AuthorizationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultAuthorizationRuleService implements AuthorizationRuleService {
    @Override
    public AuthorizationRule parseSslAuthorizationRule(String sslMqttCredentialsValue, String clientCommonName) throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = JacksonUtil.fromString(sslMqttCredentialsValue, SslMqttCredentials.class);
        if (sslMqttCredentials == null) {
            throw new AuthenticationException("Cannot parse SslMqttCredentials.");
        }
        Pattern pattern = Pattern.compile(sslMqttCredentials.getPatternRegEx());
        Matcher commonNameMatcher = pattern.matcher(clientCommonName);
        if (!commonNameMatcher.find()) {
            throw new AuthenticationException("Cannot find string for pattern in common name [" + clientCommonName + "]");
        }
        String mappingKey;
        try {
            mappingKey = commonNameMatcher.group(1);
        } catch (Exception e) {
            throw new AuthenticationException("Failed to extract keyword from common name [" + clientCommonName + "]");
        }
        String authorizationRulePatternRegEx = sslMqttCredentials.getAuthorizationRulesMapping().get(mappingKey);
        if (authorizationRulePatternRegEx == null) {
            throw new AuthenticationException("Cannot find authorization rule pattern for key [" + mappingKey + "]");
        }

        return new AuthorizationRule(Pattern.compile(authorizationRulePatternRegEx));
    }

    @Override
    public AuthorizationRule parseBasicAuthorizationRule(String basicMqttCredentialsValue) throws AuthenticationException {
        BasicMqttCredentials basicMqttCredentials = JacksonUtil.fromString(basicMqttCredentialsValue, BasicMqttCredentials.class);
        if (basicMqttCredentials == null) {
            throw new AuthenticationException("Cannot parse BasicMqttCredentials.");
        }
        if (basicMqttCredentials.getAuthorizationRulePattern() == null) {
            return null;
        } else {
            return new AuthorizationRule(Pattern.compile(basicMqttCredentials.getAuthorizationRulePattern()));
        }
    }

    @Override
    public void validateAuthorizationRule(AuthorizationRule authorizationRule, Collection<String> topics) throws AuthorizationException {
        if (authorizationRule == null) {
            return;
        }
        Pattern pattern = authorizationRule.getPattern();
        for (String topic : topics) {
            Matcher matcher = pattern.matcher(topic);
            if (!matcher.matches()) {
                throw new AuthorizationException(topic);
            }
        }
    }
}
