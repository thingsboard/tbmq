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
package org.thingsboard.mqtt.broker.dao.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.mqtt.broker.dao.model.AuthorizationPolicyEntity;

import java.util.UUID;

public interface AuthorizationPolicyRepository extends JpaRepository<AuthorizationPolicyEntity, UUID> {

    AuthorizationPolicyEntity findByName(String name);

    @Query("SELECT p FROM AuthorizationPolicyEntity p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :textSearch, '%'))")
    Page<AuthorizationPolicyEntity> findAll(@Param("textSearch") String textSearch, Pageable pageable);
}
