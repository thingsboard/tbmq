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
package org.thingsboard.mqtt.broker.actors.client.service.channel;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelBackpressureManagerImpl implements ChannelBackpressureManager {

    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;
    private final DevicePersistenceProcessor devicePersistenceProcessor;
    private final StatsManager statsManager;

    @Getter
    private AtomicInteger nonWritableClientsCount;

    @PostConstruct
    public void init() {
        this.nonWritableClientsCount = statsManager.createNonWritableClientsCounter();
    }

    @Override
    public void onChannelWritable(ClientActorState state) {
        if (!SessionState.CHANNEL_NON_WRITABLE.equals(state.getCurrentSessionState())) {
            log.debug("[{}] Received channel writable event when current state is not CHANNEL_NON_WRITABLE", state.getClientId());
            return;
        } else {
            log.info("[{}] Channel is writable", state.getClientId());
        }
        nonWritableClientsCount.updateAndGet(current -> current == 0 ? 0 : current - 1);
        if (state.getCurrentSessionCtx().isCleanSession()) {
            return;
        }
        state.updateSessionState(SessionState.CONNECTED);
        if (APPLICATION.equals(getClientType(state))) {
            applicationPersistenceProcessor.processChannelWritable(state);
        } else if (DEVICE.equals(getClientType(state))) {
            devicePersistenceProcessor.processChannelWritable(state.getClientId());
        }
    }

    @Override
    public void onChannelNonWritable(ClientActorState state) {
        if (!SessionState.CONNECTED.equals(state.getCurrentSessionState())) {
            log.debug("[{}] Received CHANNEL_NON_WRITABLE when current state is not CONNECTED", state.getClientId());
            return;
        } else {
            log.warn("[{}] Channel became non-writable", state.getClientId());
        }
        nonWritableClientsCount.incrementAndGet();
        if (state.getCurrentSessionCtx().isCleanSession()) {
            return;
        }
        state.updateSessionState(SessionState.CHANNEL_NON_WRITABLE);
        if (APPLICATION.equals(getClientType(state))) {
            applicationPersistenceProcessor.processChannelNonWritable(state.getClientId());
        } else if (DEVICE.equals(getClientType(state))) {
            devicePersistenceProcessor.processChannelNonWritable(state.getClientId());
        }
    }

    private ClientType getClientType(ClientActorState state) {
        return state.getCurrentSessionCtx().getClientType();
    }
}
