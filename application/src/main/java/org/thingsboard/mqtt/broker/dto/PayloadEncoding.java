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
package org.thingsboard.mqtt.broker.dto;

/**
 * Form of the {@link RestPublishRequest#getPayload()} field in the request body.
 */
public enum PayloadEncoding {
    /** The payload is a Base64 string; the broker publishes the decoded bytes. Use for binary payloads. */
    BASE64,
    /** The payload is a string; the broker publishes its UTF-8 bytes. */
    TEXT,
    /** The payload is any JSON value (object, array, string, number, boolean); the broker publishes its compact JSON text. */
    JSON
}
