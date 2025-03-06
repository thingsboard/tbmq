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
package org.thingsboard.mqtt.broker.service.install;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
@Service
@Profile("install")
public class SqlDatabaseSchemaService implements DatabaseSchemaService {

    private static final String SCHEMA_ENTITIES_SQL = "schema-entities.sql";
    private static final String SCHEMA_ENTITIES_IDX_SQL = "schema-entities-idx.sql";
    private static final String SQL_DIR = "sql";

    @Value("${spring.datasource.url}")
    protected String dbUrl;

    @Value("${spring.datasource.username}")
    protected String dbUserName;

    @Value("${spring.datasource.password}")
    protected String dbPassword;

    @Autowired
    private InstallScripts installScripts;

    @Override
    public void createDatabaseSchema() throws Exception {
        log.info("Installing SQL DataBase schema part: " + SCHEMA_ENTITIES_SQL);
        executeQueryFromFile(SCHEMA_ENTITIES_SQL);
        createDatabaseIndexes();
    }

    private void createDatabaseIndexes() throws Exception {
        log.info("Installing SQL DataBase schema indexes part: " + SCHEMA_ENTITIES_IDX_SQL);
        executeQueryFromFile(SCHEMA_ENTITIES_IDX_SQL);
    }

    void executeQueryFromFile(String schemaSql) throws SQLException, IOException {
        Path schemaFile = Paths.get(installScripts.getDataDir(), SQL_DIR, schemaSql);
        String sql = Files.readString(schemaFile);
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword)) {
            conn.createStatement().execute(sql); //NOSONAR, ignoring because method used to load initial thingsboard_mqtt_broker database schema
        }
    }
}
