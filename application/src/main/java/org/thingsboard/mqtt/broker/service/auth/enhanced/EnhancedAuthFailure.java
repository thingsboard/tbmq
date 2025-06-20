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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import lombok.Getter;

@Getter
public enum EnhancedAuthFailure {

    MISSING_AUTH_METHOD("Received AUTH message while authentication method is not set in the client session ctx!"),
    AUTH_METHOD_MISMATCH("Received AUTH message while authentication method mismatch with value from the client session ctx!"),
    MISSING_AUTH_DATA("No authentication data found!"),
    ENHANCED_AUTH_DISABLED("Enhanced authentication provider is disabled by broker configuration"),
    MISSING_SCRAM_SERVER("Received AUTH continue message while SCRAM server is null!"),
    FAILED_TO_INIT_SCRAM_SERVER("Failed to initialize SCRAM server!"),
    AUTH_CHALLENGE_FAILED("Client's proof of password knowledge was evaluated, but the authentication challenge wasn't completed successfully!"),
    CLIENT_FIRST_MESSAGE_EVALUATION_ERROR("Failed to evaluate client first message!"),
    CLIENT_FINAL_MESSAGE_EVALUATION_ERROR("Failed to verify the client's proof of password knowledge!"),
    CLIENT_RE_AUTH_MESSAGE_EVALUATION_ERROR("Failed to evaluate client re-AUTH message!"),
    INVALID_CLIENT_STATE_FOR_AUTH_PACKET("Received AUTH continue message while client is in wrong state!"),
    BLOCKED_CLIENT("Enhanced auth blocked client by ");

    private final String reasonLog;

    EnhancedAuthFailure(String reasonLog) {
        this.reasonLog = reasonLog;
    }

}
