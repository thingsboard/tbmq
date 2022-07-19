/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.DbConnectionChecker;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlDbConnectionChecker implements DbConnectionChecker {

    private static final Object OBJ = new Object();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean isDbConnected() {
        return connected.get();
    }

    @Scheduled(fixedRateString = "${db.connection-check-rate-ms:10000}")
    private void checkConnection() {
        try {
            jdbcTemplate.query("select 1", (rs, rowNum) -> OBJ);
            connected.getAndSet(true);
        } catch (Exception e) {
            log.warn("Failed to connect to the DB. Exception - {}, reason - {}.", e.getClass().getSimpleName(), e.getMessage());
            connected.getAndSet(false);
        }
    }

}
