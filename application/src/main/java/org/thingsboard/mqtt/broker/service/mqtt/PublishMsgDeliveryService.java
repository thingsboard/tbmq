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
package org.thingsboard.mqtt.broker.service.mqtt;

import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

public interface PublishMsgDeliveryService {

    void sendPublishMsgToClient(ClientSessionCtx sessionCtx, DevicePublishMsg publishMsg, boolean isDup);

    void sendPublishMsgProtoToClient(ClientSessionCtx sessionCtx, PublishMsgProto publishMsgProto);

    void sendPublishMsgProtoToClient(ClientSessionCtx sessionCtx, PublishMsgProto publishMsgProto, Subscription subscription);

    void sendPublishMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, PublishMsg publishMsg);

    void sendPublishRetainedMsgToClient(ClientSessionCtx sessionCtx, RetainedMsg retainedMsg);

    void sendPubRelMsgToClient(ClientSessionCtx sessionCtx, int packetId);

    void sendPubRelMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, int packetId);
}
