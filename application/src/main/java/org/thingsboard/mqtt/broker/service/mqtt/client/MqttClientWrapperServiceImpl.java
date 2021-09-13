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
package org.thingsboard.mqtt.broker.service.mqtt.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttClient;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionReader;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttClientWrapperServiceImpl implements MqttClientWrapperService {
    private final MqttClientService mqttClientService;
    private final ClientSessionReader clientSessionReader;
    private final TbQueueAdmin queueAdmin;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;

    @Override
    public MqttClient saveMqttClient(MqttClient mqttClient) {
        MqttClient savedClient = mqttClientService.saveMqttClient(mqttClient);

        // TODO: check if was APPLICATION and now device
        if (mqttClient.getType() == ClientType.APPLICATION) {
            String clientTopic = MqttApplicationClientUtil.getTopic(mqttClient.getClientId());
            queueAdmin.createTopic(clientTopic, applicationPersistenceMsgQueueFactory.getTopicConfigs());
        }

        return savedClient;
    }

    @Override
    public void deleteMqttClient(UUID id) throws ThingsboardException {
        MqttClient mqttClient = mqttClientService.getMqttClientById(id).orElse(null);
        if (mqttClient == null) {
            throw new ThingsboardException("Cannot find MQTT client", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (mqttClient.getType() == ClientType.APPLICATION) {
            ClientSession clientSession = clientSessionReader.getClientSession(mqttClient.getClientId());
            if (clientSession != null && clientSession.isConnected()) {
                throw new ThingsboardException("Cannot delete APPLICATION client for active session", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            // TODO: delete consumer group as well
            String clientTopic = MqttApplicationClientUtil.getTopic(mqttClient.getClientId());
            queueAdmin.deleteTopic(clientTopic);
        }

        mqttClientService.deleteMqttClient(id);
    }

    @Override
    public Optional<MqttClient> getMqttClientByClientId(String clientId) {
        // TODO: make async
        return mqttClientService.getMqttClientByClientId(clientId);
    }

    @Override
    public Optional<MqttClient> getMqttClientById(UUID id) {
        return mqttClientService.getMqttClientById(id);
    }

    @Override
    public PageData<MqttClient> getClients(PageLink pageLink) {
        return mqttClientService.getClients(pageLink);
    }
}
