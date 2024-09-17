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
package org.thingsboard.mqtt.broker.common.data.client.credentials;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasicMqttCredentials implements HasSinglePubSubAutorizationRules {

    @NoXss
    private String clientId;
    @NoXss
    private String userName;
    private String password;
    private PubSubAuthorizationRules authRules;

    public static BasicMqttCredentials newInstance(String clientId, String userName, String password, List<String> authRules) {
        return new BasicMqttCredentials(clientId, userName, password, PubSubAuthorizationRules.newInstance(authRules));
    }

    public static BasicMqttCredentials newInstance(String userName) {
        return new BasicMqttCredentials(null, userName, null, PubSubAuthorizationRules.newInstance(List.of(".*")));
    }

}
