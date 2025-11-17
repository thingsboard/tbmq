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
package org.thingsboard.mqtt.broker.actors.device;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.device.messages.DeliverPersistedMessagesEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ContextAwareActor;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;

@Slf4j
public class PersistedDeviceActor extends ContextAwareActor {

    private final PersistedDeviceActorMessageProcessor processor;
    private final ClientLogger clientLogger;
    private final String clientId;

    PersistedDeviceActor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.processor = new PersistedDeviceActorMessageProcessor(systemContext, clientId);
        this.clientLogger = systemContext.getClientActorContext().getClientLogger();
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Received {} msg.", clientId, msg.getMsgType());
        }
        clientLogger.logEvent(clientId, getClass(), ctx -> ctx
                .msg("Process actor msg")
                .kv(StatsConstantNames.MSG_TYPE, msg.getMsgType())
        );
        switch (msg.getMsgType()) {
            case DEVICE_CONNECTED_EVENT_MSG:
                processor.processDeviceConnect(ctx, (DeviceConnectedEventMsg) msg);
                break;
            case DEVICE_DISCONNECTED_EVENT_MSG:
                processor.processDeviceDisconnect(ctx);
                break;
            case SHARED_SUBSCRIPTION_EVENT_MSG:
                processor.processSharedSubscriptions(ctx, (SharedSubscriptionEventMsg) msg);
                break;
            case INCOMING_PUBLISH_MSG:
                processor.processIncomingMsg((IncomingPublishMsg) msg);
                break;
            case PACKET_ACKNOWLEDGED_EVENT_MSG:
                processor.processPacketAcknowledge((PacketAcknowledgedEventMsg) msg);
                break;
            case PACKET_RECEIVED_EVENT_MSG:
                processor.processPacketReceived((PacketReceivedEventMsg) msg);
                break;
            case PACKET_RECEIVED_NO_DELIVERY_EVENT_MSG:
                processor.processPacketReceivedNoDelivery((PacketReceivedNoDeliveryEventMsg) msg);
                break;
            case PACKET_COMPLETED_EVENT_MSG:
                processor.processPacketComplete((PacketCompletedEventMsg) msg);
                break;
            case STOP_DEVICE_ACTOR_COMMAND_MSG:
                processor.processActorStop(ctx, (StopDeviceActorCommandMsg) msg);
                break;
            case REMOVE_PERSISTED_MESSAGES_MSG:
                processor.processRemovePersistedMessages();
                break;
            case DEVICE_WRITABLE_CHANNEL_MSG:
                processor.processChannelWritable(ctx);
                break;
            case DEVICE_NON_WRITABLE_CHANNEL_MSG:
                processor.processChannelNonWritable();
                break;
            case DEVICE_DELIVER_PERSISTED_MESSAGES_MSG:
                processor.processDeliverPersistedMessages((DeliverPersistedMessagesEventMsg) msg);
                break;
            default:
                return false;
        }
        return true;
    }
}
