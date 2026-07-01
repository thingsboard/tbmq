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
package org.thingsboard.mqtt.broker.integration.service.processing.backpressure;

import lombok.Getter;
import lombok.Setter;

/**
 * Shared ack-strategy properties for the integration-executor consume loops. Concrete subclasses only supply the
 * {@code @ConfigurationProperties} prefix so the data and lifecycle-event streams bind independently while a new
 * field is declared here once. Left abstract (no {@code @ConfigurationProperties}) so it is never bound as a bean
 * on its own, keeping the two concrete configs unambiguous to inject by type.
 */
@Getter
@Setter
public abstract class IntegrationAckStrategyProperties {

    private IntegrationAckStrategyType type;
    private int retries;
    private int pauseBetweenRetries;

}
