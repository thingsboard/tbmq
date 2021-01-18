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
package org.thingsboard.mqtt.broker.util;

import org.thingsboard.mqtt.broker.exception.AuthorizationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuthUtil {
    public static void validateAuthorizationRule(AuthorizationRule authorizationRule, Collection<String> topics) {
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
