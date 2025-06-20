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
package org.thingsboard.mqtt.broker.service.security.authorization;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.regex.Pattern;

@AllArgsConstructor
@Getter
@ToString
public class AuthRulePatterns {

    private final List<Pattern> pubPatterns;
    private final List<Pattern> subPatterns;

    public static AuthRulePatterns newInstance(List<Pattern> patterns) {
        return new AuthRulePatterns(patterns, patterns);
    }

    public static AuthRulePatterns of(List<Pattern> pubPatterns, List<Pattern> subPatterns) {
        return new AuthRulePatterns(pubPatterns, subPatterns);
    }

}
