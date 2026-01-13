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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import lombok.Builder;

@Builder
public record EnhancedAuthContinueResponse(boolean success,
                                           String username,
                                           byte[] response,
                                           EnhancedAuthFailure enhancedAuthFailure) {

    public static EnhancedAuthContinueResponse success(String username, byte[] response) {
        return EnhancedAuthContinueResponse.builder()
                .success(true)
                .username(username)
                .response(response)
                .build();
    }

    public static EnhancedAuthContinueResponse failure(EnhancedAuthFailure enhancedAuthFailure) {
        return EnhancedAuthContinueResponse.failure(null, enhancedAuthFailure);
    }

    public static EnhancedAuthContinueResponse failure(String username, EnhancedAuthFailure enhancedAuthFailure) {
        return EnhancedAuthContinueResponse.builder()
                .success(false)
                .username(username)
                .enhancedAuthFailure(enhancedAuthFailure)
                .build();
    }

}
