/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ContextAwareActor;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;

@Slf4j
public class PersistedDeviceActor extends ContextAwareActor {
    private final PersistedDeviceActorMessageProcessor processor;
    private final ClientLogger clientLogger;
    private final String clientId;

    // TODO: merge it with Client Actor
    PersistedDeviceActor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.processor = new PersistedDeviceActorMessageProcessor(systemContext, clientId);
        this.clientLogger = systemContext.getClientLogger();
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        log.trace("[{}] Received {} msg.", clientId, msg.getMsgType());
        clientLogger.logEvent(clientId, "Received DEVICE actor msg - " + msg.getMsgType());
        switch (msg.getMsgType()) {
            case DEVICE_CONNECTED_EVENT_MSG:
                processor.processDeviceConnect((DeviceConnectedEventMsg) msg);
                break;
            case DEVICE_DISCONNECTED_EVENT_MSG:
                processor.processDeviceDisconnect(ctx);
                break;
            case INCOMING_PUBLISH_MSG:
                processor.process((IncomingPublishMsg) msg);
                break;
            case PACKET_ACKNOWLEDGED_EVENT_MSG:
                processor.processPacketAcknowledge((PacketAcknowledgedEventMsg) msg);
                break;
            case PACKET_RECEIVED_EVENT_MSG:
                processor.processPacketReceived((PacketReceivedEventMsg) msg);
                break;
            case PACKET_COMPLETED_EVENT_MSG:
                processor.processPacketComplete((PacketCompletedEventMsg) msg);
                break;
            case STOP_DEVICE_ACTOR_COMMAND_MSG:
                processor.processActorStop(ctx, (StopDeviceActorCommandMsg) msg);
                break;
            default:
                return false;
        }
        return true;
    }
}
