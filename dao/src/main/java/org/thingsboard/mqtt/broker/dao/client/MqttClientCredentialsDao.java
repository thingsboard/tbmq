/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.Dao;

import java.util.List;

public interface MqttClientCredentialsDao extends Dao<MqttClientCredentials> {

    MqttClientCredentials findByCredentialsId(String credentialsId);

    List<MqttClientCredentials> findAllByCredentialsIds(List<String> credentialIds);

    MqttClientCredentials findSystemWebSocketCredentials();

    PageData<MqttClientCredentials> findAll(PageLink pageLink);

    PageData<MqttClientCredentials> findAllV2(ClientCredentialsQuery query);

    boolean existsByCredentialsType(ClientCredentialsType credentialsType);
}
