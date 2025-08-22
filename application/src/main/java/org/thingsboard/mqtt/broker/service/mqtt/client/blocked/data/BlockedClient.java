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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RegexBlockedClient.class, name = "REGEX"),
        @JsonSubTypes.Type(value = ClientIdBlockedClient.class, name = "CLIENT_ID"),
        @JsonSubTypes.Type(value = UsernameBlockedClient.class, name = "USERNAME"),
        @JsonSubTypes.Type(value = IpAddressBlockedClient.class, name = "IP_ADDRESS")})
public interface BlockedClient {

    @JsonIgnore
    String getKey();

    BlockedClientType getType();

    long getExpirationTime();

    @JsonIgnore
    boolean isExpired();

    @NoXss
    String getDescription();

    @NoXss
    @JsonIgnore
    String getValue();

    default RegexMatchTarget getRegexMatchTarget() {
        return null;
    }
}
