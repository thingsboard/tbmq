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
package org.thingsboard.mqtt.broker.service.stats;

/**
 * Classification of connection-establishment failures observed at {@code MqttSessionHandler.exceptionCaught}.
 * Carried on {@link ConnectionStats#onConnectionError(ConnectionErrorType)} so the PE extension can tag
 * {@code connectionError{type}}; the CE implementation ignores it and increments a single untagged counter.
 */
public enum ConnectionErrorType {
    SSL_HANDSHAKE,
    NOT_SSL_RECORD,
    IO,
    PROTOCOL_VIOLATION,
    OTHER
}
