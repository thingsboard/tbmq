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
package org.thingsboard.mqtt.broker.dao.sqlts.latest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.thingsboard.mqtt.broker.dao.model.sqlts.latest.TsKvLatestEntity;

import java.util.Collection;
import java.util.List;

@Repository
public class SearchTsKvLatestRepository {

    public static final String FIND_ALL_BY_ENTITY_ID = "findAllByEntityId";
    public static final String FIND_ALL_BY_ENTITY_IDS = "findAllByEntityIds";
    public static final String FIND_ALL_BY_ENTITY_IDS_AND_KEYS = "findAllByEntityIdsAndKeys";

    private static final String SELECT_LATEST = "SELECT ts_kv_latest.entity_id AS entityId, ts_kv_latest.key AS key, ts_kv_dictionary.key AS strKey," +
            " ts_kv_latest.long_v AS longValue, ts_kv_latest.ts AS ts FROM ts_kv_latest " +
            "INNER JOIN ts_kv_dictionary ON ts_kv_latest.key = ts_kv_dictionary.key_id ";

    public static final String FIND_ALL_BY_ENTITY_ID_QUERY = SELECT_LATEST + "WHERE ts_kv_latest.entity_id = :id";
    public static final String FIND_ALL_BY_ENTITY_IDS_QUERY = SELECT_LATEST + "WHERE ts_kv_latest.entity_id IN (:ids)";
    public static final String FIND_ALL_BY_ENTITY_IDS_AND_KEYS_QUERY = FIND_ALL_BY_ENTITY_IDS_QUERY + " AND ts_kv_dictionary.key IN (:keys)";

    @PersistenceContext
    private EntityManager entityManager;

    public List<TsKvLatestEntity> findAllByEntityId(String entityId) {
        return entityManager.createNamedQuery(FIND_ALL_BY_ENTITY_ID, TsKvLatestEntity.class)
                .setParameter("id", entityId)
                .getResultList();
    }

    /**
     * Latest values of several entities in one query; {@code keys} null means every key.
     */
    public List<TsKvLatestEntity> findAllByEntityIds(Collection<String> entityIds, Collection<String> keys) {
        if (keys == null) {
            return entityManager.createNamedQuery(FIND_ALL_BY_ENTITY_IDS, TsKvLatestEntity.class)
                    .setParameter("ids", entityIds)
                    .getResultList();
        }
        return entityManager.createNamedQuery(FIND_ALL_BY_ENTITY_IDS_AND_KEYS, TsKvLatestEntity.class)
                .setParameter("ids", entityIds)
                .setParameter("keys", keys)
                .getResultList();
    }

}
