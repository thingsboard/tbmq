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
package org.thingsboard.mqtt.broker.standalone.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionEventResponseSender;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;

@Component
@StandaloneComponent
@RequiredArgsConstructor
public class LocalClientSessionEventResponseSender implements ClientSessionEventResponseSender {

    private final PendingConnectionRequests pendingConnectionRequests;

    @Override
    public void send(String clientId, ConnectionRequestInfo requestInfo, ClientSessionEventResponseProto response) {
        pendingConnectionRequests.complete(requestInfo.getRequestId(), ProtoConverter.toConnectionResponse(response));
    }

}
