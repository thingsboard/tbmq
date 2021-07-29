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
    public void deleteMqttClient(String clientId) throws ThingsboardException {
        MqttClient mqttClient = mqttClientService.getMqttClient(clientId).orElse(null);
        if (mqttClient == null) {
            throw new ThingsboardException("Cannot find MQTT client", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (mqttClient.getType() == ClientType.APPLICATION) {
            ClientSession clientSession = clientSessionReader.getClientSession(clientId);
            if (clientSession != null && clientSession.isConnected()) {
                throw new ThingsboardException("Cannot delete APPLICATION client for active session", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            // TODO: delete consumer group as well
            String clientTopic = MqttApplicationClientUtil.getTopic(clientId);
            queueAdmin.deleteTopic(clientTopic);
        }

        mqttClientService.deleteMqttClient(clientId);
    }

    @Override
    public Optional<MqttClient> getMqttClient(String clientId) {
        return mqttClientService.getMqttClient(clientId);
    }

    @Override
    public PageData<MqttClient> getClients(PageLink pageLink) {
        return mqttClientService.getClients(pageLink);
    }
}
