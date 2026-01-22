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
package org.thingsboard.mqtt.broker.service.auth.providers;

public enum AuthStatus {

    /**
     * Client is successfully authenticated.
     */
    SUCCESS,

    /**
     * Authentication failed. The connection should be rejected immediately.
     */
    FAILURE,

    /**
     * This provider could not authenticate the client (e.g., credentials not found),
     * and the broker should attempt authentication with the next available provider.
     */
    SKIPPED
}
