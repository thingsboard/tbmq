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
package org.thingsboard.mqtt.broker.common.data.util;

import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AuthRulesUtil {

    public static void validateAndCompileAuthRules(PubSubAuthorizationRules authRules) {
        if (authRules == null) {
            throw new DataValidationException("AuthRules are null!");
        }
        compileAuthRules(authRules.getPubAuthRulePatterns(), "Publish auth rule patterns should be a valid regexes!");
        compileAuthRules(authRules.getSubAuthRulePatterns(), "Subscribe auth rule patterns should be a valid regexes!");
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

}
