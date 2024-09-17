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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class EnhancedAuthResponse {

    private final boolean success;
    private final ClientType clientType;
    private final List<AuthRulePatterns> authRulePatterns;
    private final MqttConnectReturnCode failureReasonCode;

    public static EnhancedAuthResponse success(ClientType clientType, List<AuthRulePatterns> authRulePatterns) {
        return EnhancedAuthResponse.builder()
                .success(true)
                .clientType(clientType)
                .authRulePatterns(authRulePatterns)
                .build();
    }

    public static EnhancedAuthResponse failure() {
        return failure(MqttConnectReturnCode.CONNECTION_REFUSED_UNSPECIFIED_ERROR);
    }

    public static EnhancedAuthResponse failure(MqttConnectReturnCode failureReturnCode) {
        return EnhancedAuthResponse.builder()
                .success(false)
                .failureReasonCode(failureReturnCode)
                .build();
    }

}
