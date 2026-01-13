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
package org.thingsboard.mqtt.broker.common.data.util;

import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AuthRulesUtil {

    public static final String COMMON_NAME_PLACEHOLDER = "${cn}";
    public static final String DUMMY_CN = "tbmq.io";
    public static final String PUB_AUTH_RULE_PATTERNS_ERROR_MSG = "Publish auth rule patterns should be a valid regexes!";
    public static final String SUB_AUTH_RULE_PATTERNS_ERROR_MSG = "Subscribe auth rule patterns should be a valid regexes!";

    public static void validateAndCompileAuthRules(PubSubAuthorizationRules authRules) {
        checkNotNull(authRules);
        compileAuthRules(authRules.getPubAuthRulePatterns(), PUB_AUTH_RULE_PATTERNS_ERROR_MSG);
        compileAuthRules(authRules.getSubAuthRulePatterns(), SUB_AUTH_RULE_PATTERNS_ERROR_MSG);
    }

    public static void validateAndCompileSslAuthRules(PubSubAuthorizationRules authRules) {
        checkNotNull(authRules);
        compileAuthRules(replaceWithDummyCn(authRules.getPubAuthRulePatterns()), PUB_AUTH_RULE_PATTERNS_ERROR_MSG);
        compileAuthRules(replaceWithDummyCn(authRules.getSubAuthRulePatterns()), SUB_AUTH_RULE_PATTERNS_ERROR_MSG);
    }

    public static List<Pattern> fromStringList(List<String> authRules) {
        if (CollectionUtils.isEmpty(authRules)) {
            return Collections.emptyList();
        }
        return authRules.stream().map(Pattern::compile).toList();
    }

    private static void compileAuthRules(List<String> authRules, String message) {
        if (!CollectionUtils.isEmpty(authRules)) {
            try {
                authRules.forEach(Pattern::compile);
            } catch (PatternSyntaxException e) {
                throw new DataValidationException(message);
            }
        }
    }

    private static List<String> replaceWithDummyCn(List<String> rules) {
        if (CollectionUtils.isEmpty(rules)) {
            return Collections.emptyList();
        }
        return rules.stream()
                .map(p -> processPattern(p, DUMMY_CN))
                .toList();
    }

    public static String processPattern(String pattern, String clientCommonName) {
        try {
            return processVar(pattern, clientCommonName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process pattern!", e);
        }
    }

    private static String processVar(String pattern, String value) {
        String quoted = Pattern.quote(value);
        return pattern.replace(COMMON_NAME_PLACEHOLDER, quoted);
    }

    private static void checkNotNull(PubSubAuthorizationRules authRules) {
        if (authRules == null) {
            throw new DataValidationException("AuthRules are null!");
        }
    }

}
