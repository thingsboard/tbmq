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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

@Slf4j
@Service
@RequiredArgsConstructor
public class TbmqIntegrationApiServiceImpl implements TbmqIntegrationApiService {

    private final PlatformIntegrationService platformIntegrationService;

    @Override
    public void handle(TbProtoQueueMsg<UplinkIntegrationMsgProto> envelope, TbCallback callback) {
        var msg = envelope.getValue();
        if (msg.hasEventProto()) {
            platformIntegrationService.processUplinkData(msg.getEventProto(), new IntegrationApiCallback(callback));
        } else if (msg.hasServiceInfoProto()) {
            platformIntegrationService.processServiceInfo(msg.getServiceInfoProto());
        } else {
            callback.onFailure(new IllegalArgumentException("Unsupported integration msg!"));
        }
    }

}
