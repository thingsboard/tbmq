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
package org.thingsboard.mqtt.broker.actors.client.service.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelBackpressureManagerImpl implements ChannelBackpressureManager {

    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;
    private final DevicePersistenceProcessor devicePersistenceProcessor;

    @Override
    public void onChannelWritable(ClientActorState state) {
        if (APPLICATION.equals(state.getCurrentSessionCtx().getClientType())) {
            applicationPersistenceProcessor.processChannelWritable(state);
        } else if (DEVICE.equals(state.getCurrentSessionCtx().getClientType())) {
            devicePersistenceProcessor.processChannelWritable(state.getClientId());
        }
    }

    @Override
    public void onChannelNonWritable(ClientActorState state) {
        if (APPLICATION.equals(state.getCurrentSessionCtx().getClientType())) {
            applicationPersistenceProcessor.processChannelNonWritable(state.getClientId());
        } else if (DEVICE.equals(state.getCurrentSessionCtx().getClientType())) {
            devicePersistenceProcessor.processChannelNonWritable(state.getClientId());
        }
    }
}
