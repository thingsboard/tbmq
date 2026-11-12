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
package org.thingsboard.mqtt.broker.integration.api;

/**
 * Connection pool bounds of the HTTP integration client (integrations.http.pool).
 *
 * @param maxConnections maximum number of pooled connections; non-positive = Reactor Netty default
 * @param maxIdleTimeMs  close a pooled connection idle this long; non-positive = never
 * @param maxLifeTimeMs  close a pooled connection this old; non-positive = never
 */
public record HttpClientPoolSettings(int maxConnections, long maxIdleTimeMs, long maxLifeTimeMs) {
}
