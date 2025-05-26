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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@EqualsAndHashCode(of = {"pattern", "regexMatchTarget"}, callSuper = false)
@Data
@NoArgsConstructor
public class RegexBlockedClient extends AbstractBlockedClient {

    private String pattern;
    private RegexMatchTarget regexMatchTarget;

    @JsonIgnore
    private transient Pattern compiledPattern;

    public RegexBlockedClient(String pattern, RegexMatchTarget regexMatchTarget) {
        super();
        this.pattern = pattern;
        this.regexMatchTarget = regexMatchTarget;
        this.compiledPattern = Pattern.compile(pattern);
    }

    public RegexBlockedClient(long expirationTime, String description, String pattern, RegexMatchTarget regexMatchTarget) {
        super(expirationTime, description);
        this.pattern = pattern;
        this.regexMatchTarget = regexMatchTarget;
        this.compiledPattern = Pattern.compile(pattern);
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.compiledPattern = Pattern.compile(pattern);
    }

    @Override
    public BlockedClientType getType() {
        return BlockedClientType.REGEX;
    }

    @Override
    public String getValue() {
        return pattern;
    }

    public boolean matches(String input) {
        return compiledPattern.matcher(input).matches();
    }
}
