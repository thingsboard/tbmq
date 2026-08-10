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
package org.thingsboard.mqtt.broker.service.limits;

/**
 * Outcome of a quota charge that the caller is able to defer.
 *
 * @param granted   number of packets granted, in {@code [0..n]}
 * @param exhausted {@code true} when a draw confirmed the shared bucket dry, so the ungranted
 *                  remainder is a terminal loss; {@code false} when the shortfall is node-local with a
 *                  draw in flight, so the remainder should be retried rather than destroyed
 */
public record QuotaGrant(int granted, boolean exhausted) {
}
