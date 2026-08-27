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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Operation that was denied during authorization, carried in the {@code action} field of a
 * CLIENT_AUTHORIZATION_FAILED lifecycle event. The {@link #label} is the lowercase wire value emitted to
 * integrations, keeping callers from hand-typing the literal.
 */
@Getter
@RequiredArgsConstructor
public enum AuthorizationAction {

    PUBLISH("publish"),
    SUBSCRIBE("subscribe");

    private final String label;

}
