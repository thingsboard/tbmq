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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.dao.model.MqttClientCredentialsEntity;

import java.util.List;
import java.util.UUID;

public interface MqttClientCredentialsRepository extends JpaRepository<MqttClientCredentialsEntity, UUID> {

    MqttClientCredentialsEntity findByCredentialsId(String credentialsId);

    MqttClientCredentialsEntity findMqttClientCredentialsEntityByName(String name);

    List<MqttClientCredentialsEntity> findByCredentialsIdIn(List<String> credentialsIds);

    boolean existsByCredentialsType(ClientCredentialsType credentialsType);

    @Query("SELECT c FROM MqttClientCredentialsEntity c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :textSearch, '%'))")
    Page<MqttClientCredentialsEntity> findAll(@Param("textSearch") String textSearch,
                                              Pageable pageable);

    // TODO: improve performance of this method. Change credentialsValue to JSONB and add GIN index(es)?
    // Not very accurate search because of "%param% :searchString %" - second % makes the substring to search not within the actual value
    @Query("SELECT c FROM MqttClientCredentialsEntity c WHERE " +
            "((:clientTypes) IS NULL OR c.clientType IN (:clientTypes)) " +
            "AND ((:clientCredentialsTypes) IS NULL OR c.credentialsType IN (:clientCredentialsTypes)) " +
            "AND LOWER(c.name) LIKE LOWER(CONCAT('%', :textSearch, '%')) " +
            "AND (" +
            // Return all credentials if all parameters are empty
            "    (:certificateCn = '' AND :username = '' AND :clientId = '') " +
            // Return nothing if all parameters are provided
            "    OR (:certificateCn <> '' AND :username <> '' AND :clientId <> '' AND 1 = 0) " +
            // Search within MQTT_BASIC using username
            "    OR (:username <> '' AND :certificateCn = '' AND :clientId = '' AND (c.credentialsType = 'MQTT_BASIC' OR c.credentialsType = 'SCRAM') " +
            "        AND LOWER(c.credentialsValue) LIKE LOWER(CONCAT('%\"userName\":\"%', :username, '%'))) " +
            // Search within MQTT_BASIC using clientId
            "    OR (:clientId <> '' AND :certificateCn = '' AND :username = '' AND c.credentialsType = 'MQTT_BASIC' " +
            "        AND LOWER(c.credentialsValue) LIKE LOWER(CONCAT('%\"clientId\":\"%', :clientId, '%'))) " +
            // Search within X_509 using certificateCn
            "    OR (:certificateCn <> '' AND :username = '' AND :clientId = '' AND c.credentialsType = 'X_509' " +
            "        AND LOWER(c.credentialsValue) LIKE LOWER(CONCAT('%\"certCnPattern\":\"%', :certificateCn, '%'))) " +
            // Return nothing if certificateCn and username are both provided
            "    OR (:certificateCn <> '' AND :username <> '' AND 1 = 0) " +
            // Return nothing if certificateCn and clientId are both provided
            "    OR (:certificateCn <> '' AND :clientId <> '' AND 1 = 0) " +
            // Search within MQTT_BASIC using both clientId and username
            "    OR (:username <> '' AND :clientId <> '' AND :certificateCn = '' AND c.credentialsType = 'MQTT_BASIC' " +
            "        AND LOWER(c.credentialsValue) LIKE LOWER(CONCAT('%\"userName\":\"%', :username, '%')) " +
            "        AND LOWER(c.credentialsValue) LIKE LOWER(CONCAT('%\"clientId\":\"%', :clientId, '%'))) " +
            ")")
    Page<MqttClientCredentialsEntity> findAllV2(@Param("clientTypes") List<ClientType> clientTypes,
                                                @Param("clientCredentialsTypes") List<ClientCredentialsType> clientCredentialsTypes,
                                                @Param("textSearch") String textSearch,
                                                @Param("username") String username,
                                                @Param("clientId") String clientId,
                                                @Param("certificateCn") String certificateCn,
                                                Pageable pageable);

    List<MqttClientCredentialsEntity> findByCredentialsType(ClientCredentialsType credentialsType);

}
