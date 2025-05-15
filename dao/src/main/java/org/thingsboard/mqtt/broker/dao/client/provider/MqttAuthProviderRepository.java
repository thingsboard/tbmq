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
package org.thingsboard.mqtt.broker.dao.client.provider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.mqtt.broker.dao.model.MqttAuthProviderEntity;

import java.util.UUID;

public interface MqttAuthProviderRepository extends JpaRepository<MqttAuthProviderEntity, UUID> {

    @Query("SELECT p FROM MqttAuthProviderEntity p WHERE " +
           "LOWER(p.type) LIKE LOWER(CONCAT('%', :textSearch, '%'))")
    Page<MqttAuthProviderEntity> findAll(@Param("textSearch") String textSearch,
                                         Pageable pageable);

    @Query("SELECT p FROM MqttAuthProviderEntity p WHERE " +
           "LOWER(p.type) LIKE LOWER(CONCAT('%', :textSearch, '%')) AND p.enabled = true")
    Page<MqttAuthProviderEntity> findAllEnabled(@Param("textSearch") String textSearch,
                                                Pageable pageable);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MqttAuthProviderEntity p SET p.enabled = :enabled WHERE p.id = :id")
    int updateEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);

}
