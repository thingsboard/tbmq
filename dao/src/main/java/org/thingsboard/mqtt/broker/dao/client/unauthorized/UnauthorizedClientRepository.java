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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.model.UnauthorizedClientEntity;

import java.util.List;

public interface UnauthorizedClientRepository extends JpaRepository<UnauthorizedClientEntity, String> {

    UnauthorizedClientEntity findByClientId(String clientId);

    @Query("SELECT c FROM UnauthorizedClientEntity c WHERE " +
            "(LOWER(c.clientId) LIKE LOWER(CONCAT('%', :clientId, '%'))) " +
            "AND (LOWER(c.ipAddress) LIKE LOWER(CONCAT('%', :ipAddress, '%'))) " +
            "AND ((:username = '' AND c.username IS NULL) OR LOWER(c.username) LIKE LOWER(CONCAT('%', :username, '%'))) " +
            "AND (LOWER(c.reason) LIKE LOWER(CONCAT('%', :reason, '%'))) " +
            "AND ((:startTime) IS NULL OR c.ts >= :startTime) " +
            "AND ((:endTime) IS NULL OR c.ts <= :endTime) " +
            "AND ((:passwordProvidedList) IS NULL OR c.passwordProvided IN (:passwordProvidedList)) " +
            "AND ((:tlsUsedList) IS NULL OR c.tlsUsed IN (:tlsUsedList)) " +
            "AND LOWER(c.clientId) LIKE LOWER(CONCAT('%', :textSearch, '%'))")
    Page<UnauthorizedClientEntity> findAllV2(@Param("clientId") String clientId,
                                             @Param("ipAddress") String ipAddress,
                                             @Param("username") String username,
                                             @Param("reason") String reason,
                                             @Param("startTime") Long startTime,
                                             @Param("endTime") Long endTime,
                                             @Param("passwordProvidedList") List<Boolean> passwordProvidedList,
                                             @Param("tlsUsedList") List<Boolean> tlsUsedList,
                                             @Param("textSearch") String textSearch,
                                             Pageable pageable);

    @Query("SELECT c FROM UnauthorizedClientEntity c WHERE " +
            "LOWER(c.clientId) LIKE LOWER(CONCAT('%', :textSearch, '%'))")
    Page<UnauthorizedClientEntity> findAll(@Param("textSearch") String textSearch,
                                           Pageable pageable);

    @Transactional
    @Modifying
    @Query("DELETE FROM UnauthorizedClientEntity c WHERE " +
            "(:expirationTime IS NULL OR c.ts < :expirationTime)"
    )
    void cleanupUnauthorizedClients(@Param("expirationTime") Long expirationTime);

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE unauthorized_client", nativeQuery = true)
    void deleteAll();
}
