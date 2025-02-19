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
package org.thingsboard.mqtt.broker.service;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationRequestProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto;

public interface IntegrationManagerService {

    /**
     * TBMQ-IE service methods
     */

    void handleIntegrationLifecycleMsg(IntegrationLifecycleMsg integrationLifecycleMsg);

    void handleStopIntegrationLifecycleMsg(IntegrationLifecycleMsg integrationLifecycleMsg);

    void handleValidationRequest(IntegrationValidationRequestProto validationRequestMsg, TbCallback callback);

    int getIntegrationConnectionCheckApiRequestTimeoutSec();

    void proceedGracefulShutdown();

    /**
     * TBMQ service methods
     */

    ListenableFuture<Void> validateIntegrationConfiguration(Integration integration);

    ListenableFuture<Void> checkIntegrationConnection(Integration integration);

    void handleValidationResponse(IntegrationValidationResponseProto validationResponseProto, TbCallback callback);
}
