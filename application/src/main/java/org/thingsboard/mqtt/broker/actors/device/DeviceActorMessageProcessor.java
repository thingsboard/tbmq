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

package org.thingsboard.mqtt.broker.actors.device;

import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.device.messages.DeliverPersistedMessagesEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;

public interface DeviceActorMessageProcessor {

    void processDeviceConnect(TbActorCtx actorCtx, DeviceConnectedEventMsg msg);

    void processDeviceDisconnect(TbActorCtx actorCtx);

    void processSharedSubscriptions(TbActorCtx actorCtx, SharedSubscriptionEventMsg msg);

    void processIncomingMsg(IncomingPublishMsg msg);

    void processPacketAcknowledge(PacketAcknowledgedEventMsg msg);

    void processPacketReceived(PacketReceivedEventMsg msg);

    void processPacketReceivedNoDelivery(PacketReceivedNoDeliveryEventMsg msg);

    void processPacketComplete(PacketCompletedEventMsg msg);

    void processActorStop(TbActorCtx ctx, StopDeviceActorCommandMsg msg);

    void processRemovePersistedMessages();

    void processChannelWritable(TbActorCtx actorCtx);

    void processChannelNonWritable();

    void processDeliverPersistedMessages(DeliverPersistedMessagesEventMsg msg);

}
