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
package org.thingsboard.mqtt.broker.dao.client;

import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

/**
 * The client types an edition accepts in MQTT client credentials and in the JWT and HTTP auth provider
 * configurations. The cluster edition accepts every type; the standalone edition refuses the APPLICATION type.
 */
public interface ClientTypeConstraint {

    String LITE_MESSAGE = "APPLICATION clients are not supported in TBMQ Lite";

    /**
     * @param clientType the client type of the credentials or auth provider configuration to save; {@code null} passes
     * @throws DataValidationException with {@link #LITE_MESSAGE} when the edition refuses the type
     */
    void check(ClientType clientType);

}
