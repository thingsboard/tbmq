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

import lombok.Getter;

@Getter
public enum EnhancedAuthFailureReason {

    MISSING_AUTH_METHOD("Received AUTH message while authentication method is not set in the client session ctx!"),
    AUTH_METHOD_MISMATCH("Received AUTH message while authentication method mismatch with value from the session ctx!"),
    MISSING_AUTH_DATA("No authentication data found!"),
    MISSING_SCRAM_SERVER("Received AUTH continue message while saslServer is null!"),
    AUTH_CHALLENGE_FAILED("Client's proof of password knowledge was evaluated, but the authentication challenge failed!"),
    EVALUATION_ERROR("Failed to verify the client's proof of password knowledge!");

    private final String reasonLog;

    EnhancedAuthFailureReason(String reasonLog) {
        this.reasonLog = reasonLog;
    }

}
