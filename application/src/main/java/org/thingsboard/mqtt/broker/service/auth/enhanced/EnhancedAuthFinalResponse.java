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

import lombok.Builder;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.List;

@Builder
public record EnhancedAuthFinalResponse(boolean success,
                                        byte[] response,
                                        ClientType clientType,
                                        List<AuthRulePatterns> authRulePatterns,
                                        EnhancedAuthFailureReason enhancedAuthFailureReason) {

    public static EnhancedAuthFinalResponse success(ClientType clientType, List<AuthRulePatterns> authRulePatterns, byte[] response) {
        return EnhancedAuthFinalResponse.builder()
                .success(true)
                .response(response)
                .clientType(clientType)
                .authRulePatterns(authRulePatterns)
                .build();
    }

    public static EnhancedAuthFinalResponse failure(EnhancedAuthFailureReason enhancedAuthFailureReason) {
        return EnhancedAuthFinalResponse.builder()
                .success(false)
                .enhancedAuthFailureReason(enhancedAuthFailureReason)
                .build();
    }

}
