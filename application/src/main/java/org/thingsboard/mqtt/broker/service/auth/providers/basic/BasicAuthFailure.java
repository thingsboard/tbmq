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
package org.thingsboard.mqtt.broker.service.auth.providers.basic;

import lombok.Getter;

@Getter
public enum BasicAuthFailure {

    NO_CREDENTIALS_FOUND("Basic authentication failed: no credentials found matching clientId: %s, username: %s"),
    PASSWORD_NOT_MATCH("Basic authentication failed: provided password does not match the credentials found by clientId: %s, username: %s"),
    NO_PASSWORD_PROVIDED("Basic authentication failed: password not provided to match the credentials found by clientId: %s, username: %s"),
    CAN_NOT_PARSE_PUB_SUB_RULES("Cannot parse publish-subscribe authentication rules!");

    private final String errorMsg;

    BasicAuthFailure(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
