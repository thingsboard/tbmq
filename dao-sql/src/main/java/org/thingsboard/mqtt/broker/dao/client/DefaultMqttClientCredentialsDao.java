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
package org.thingsboard.mqtt.broker.dao.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.MqttClientCredentialsEntity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultMqttClientCredentialsDao extends AbstractDao<MqttClientCredentialsEntity, MqttClientCredentials>
        implements MqttClientCredentialsDao {

    private final MqttClientCredentialsRepository mqttClientCredentialsRepository;

    @Override
    protected Class<MqttClientCredentialsEntity> getEntityClass() {
        return MqttClientCredentialsEntity.class;
    }

    @Override
    protected CrudRepository<MqttClientCredentialsEntity, UUID> getCrudRepository() {
        return mqttClientCredentialsRepository;
    }

    @Override
    public MqttClientCredentials findByCredentialsId(String credentialsId) {
        log.trace("Trying to find credentials by credentials id {}", credentialsId);
        return DaoUtil.getData(mqttClientCredentialsRepository.findByCredentialsId(credentialsId));
    }

    @Override
    public List<MqttClientCredentials> findAllByCredentialsIds(List<String> credentialIds) {
        log.trace("Trying to find credentials by credentials ids {}", credentialIds);
        return DaoUtil.convertDataList(mqttClientCredentialsRepository.findByCredentialsIdIn(credentialIds));
    }

    @Override
    public MqttClientCredentials findSystemWebSocketCredentials() {
        log.trace("Trying to find system WebSocket credentials");
        return DaoUtil.getData(mqttClientCredentialsRepository.findMqttClientCredentialsEntityByName(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME));
    }

    @Override
    public MqttClientCredentials findCredentialsByName(String name) {
        log.trace("Trying to find MQTT client credentials by name");
        return DaoUtil.getData(mqttClientCredentialsRepository.findMqttClientCredentialsEntityByName(name));
    }

    @Override
    public PageData<MqttClientCredentials> findAll(PageLink pageLink) {
        log.trace("Trying to find credentials by pageLink {}", pageLink);
        return DaoUtil.toPageData(mqttClientCredentialsRepository.findAll(
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public PageData<MqttClientCredentials> findAllV2(ClientCredentialsQuery query) {
        log.trace("Trying to find credentials by query {}", query);
        List<ClientType> clientTypes = CollectionUtils.isEmpty(query.getClientTypeList()) ? null : query.getClientTypeList();
        List<ClientCredentialsType> clientCredentialsTypes = CollectionUtils.isEmpty(query.getCredentialsTypeList()) ? null : query.getCredentialsTypeList();

        return DaoUtil.toPageData(mqttClientCredentialsRepository.findAllV2(
                clientTypes,
                clientCredentialsTypes,
                Objects.toString(query.getPageLink().getTextSearch(), ""),
                Objects.toString(query.getUsername(), ""),
                Objects.toString(query.getClientId(), ""),
                Objects.toString(query.getCertificateCn(), ""),
                DaoUtil.toPageable(query.getPageLink())));
    }

    @Override
    public boolean existsByCredentialsType(ClientCredentialsType credentialsType) {
        log.trace("Trying to check if credentials exist by type {}", credentialsType);
        return mqttClientCredentialsRepository.existsByCredentialsType(credentialsType);
    }

    @Override
    public List<MqttClientCredentials> findByCredentialsType(ClientCredentialsType type) {
        log.trace("Trying to find credentials by type {}", type);
        List<MqttClientCredentialsEntity> mqttClientCredentialsEntities = mqttClientCredentialsRepository.findByCredentialsType(type);
        return mqttClientCredentialsEntities.stream()
                .map(DaoUtil::getData)
                .collect(Collectors.toList());
    }
}
