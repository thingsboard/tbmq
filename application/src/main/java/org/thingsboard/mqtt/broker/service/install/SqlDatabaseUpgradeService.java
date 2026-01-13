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
package org.thingsboard.mqtt.broker.service.install;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class SqlDatabaseUpgradeService implements DatabaseEntitiesUpgradeService {

    private static final String SCHEMA_UPDATE_SQL = "schema_update.sql";

    private final InstallScripts installScripts;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upgradeDatabase() throws Exception {
        Path basicSqlFilePath = getSchemaUpdateFile("basic");
        if (!Files.exists(basicSqlFilePath)) {
            // If the file does not exist, it means that we don't have schema changes for this release.
            return;
        }
        log.info("Updating schema...");
        loadSql(basicSqlFilePath);
        log.info("Schema updated.");
    }

    private Path getSchemaUpdateFile(String version) {
        return Paths.get(installScripts.getAppDataDir(), "upgrade", version, SCHEMA_UPDATE_SQL);
    }

    private void loadSql(Path sqlFile) {
        String sql;
        try {
            sql = Files.readString(sqlFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        jdbcTemplate.execute((StatementCallback<Object>) stmt -> {
            stmt.execute(sql);
            printWarnings(stmt);
            return null;
        });
    }

    private void printWarnings(Statement statement) throws SQLException {
        SQLWarning warnings = statement.getWarnings();
        if (warnings != null) {
            log.info("{}", warnings.getMessage());
            SQLWarning nextWarning = warnings.getNextWarning();
            while (nextWarning != null) {
                log.info("{}", nextWarning.getMessage());
                nextWarning = nextWarning.getNextWarning();
            }
        }
    }

}
