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
package org.thingsboard.mqtt.broker.service.install;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Profile("install")
@Slf4j
public class SqlDatabaseUpgradeService implements DatabaseEntitiesUpgradeService {

    private static final String SCHEMA_UPDATE_SQL = "schema_update.sql";

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUserName;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Autowired
    private InstallScripts installScripts;

    @Override
    public void upgradeDatabase(String fromVersion) throws Exception {
        switch (fromVersion) {
            case "1.0.1":
                updateSchema("1.0.0", 1000000, "1.1.0", 1001000, conn -> {
                    try {
                        conn.createStatement().execute("ALTER TABLE device_publish_msg ADD COLUMN msg_expiry_interval int;"); //NOSONAR, ignoring because method used to execute thingsboard_mqtt_broker database upgrade script
                    } catch (Exception ignored) {
                    }
                });
                break;
            case "1.1.0":
                updateSchema("1.1.0", 1001000, "1.2.0", 1002000);
                break;
            case "1.2.0":
                updateSchema("1.2.0", 1002000, "1.2.1", 1002001, conn -> {
                    try {
                        conn.createStatement().execute("ALTER TABLE device_publish_msg ADD COLUMN payload_format_indicator int;");
                    } catch (Exception ignored) {
                    }
                    try {
                        conn.createStatement().execute("ALTER TABLE device_publish_msg ADD COLUMN content_type varchar(255);");
                    } catch (Exception ignored) {
                    }
                });
                break;
            case "1.2.1":
                updateSchema("1.2.1", 1002001, "1.3.0", 1003000, conn -> {
                    try {
                        conn.createStatement().execute("ALTER TABLE device_publish_msg ADD COLUMN response_topic varchar(255);");
                    } catch (Exception ignored) {
                    }
                    try {
                        conn.createStatement().execute("ALTER TABLE device_publish_msg ADD COLUMN correlation_data bytea;");
                    } catch (Exception ignored) {
                    }
                });
                break;
            case "1.3.0":
                updateSchema("1.3.0", 1003000, "1.3.1", 1003001);
                break;
            default:
                throw new RuntimeException("Unable to upgrade SQL database, unsupported fromVersion: " + fromVersion);
        }
    }

    private void updateSchema(String oldVersionStr, int oldVersion, String newVersionStr, int newVersion) {
        updateSchema(oldVersionStr, oldVersion, newVersionStr, newVersion, null);
    }

    private void updateSchema(String oldVersionStr, int oldVersion, String newVersionStr, int newVersion, Consumer<Connection> additionalAction) {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword)) {
            log.info("Updating schema ...");
            if (isOldSchema(conn, oldVersion)) {
                Path schemaUpdateFile = Paths.get(installScripts.getDataDir(), "upgrade", oldVersionStr, SCHEMA_UPDATE_SQL);
                if (Files.exists(schemaUpdateFile)) {
                    loadSql(schemaUpdateFile, conn);
                }
                if (additionalAction != null) {
                    additionalAction.accept(conn);
                }
                conn.createStatement().execute("UPDATE tb_schema_settings SET schema_version = " + newVersion + ";");
                log.info("Schema updated to version {}", newVersionStr);
            } else {
                log.info("Skip schema re-update to version {}. Use env flag 'SKIP_SCHEMA_VERSION_CHECK' to force the re-update.", newVersionStr);
            }
        } catch (Exception e) {
            log.error("Failed updating schema!!!", e);
        }
    }

    private void loadSql(Path sqlFile, Connection conn) throws Exception {
        String sql = Files.readString(sqlFile);
        Statement st = conn.createStatement();
        st.setQueryTimeout((int) TimeUnit.HOURS.toSeconds(3));
        st.execute(sql);//NOSONAR, ignoring because method used to execute thingsboard_mqtt_broker database upgrade script
        printWarnings(st);
        Thread.sleep(5000);
    }

    protected void printWarnings(Statement statement) throws SQLException {
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

    private boolean isOldSchema(Connection conn, long fromVersion) {
        if (getEnv("SKIP_SCHEMA_VERSION_CHECK", false)) {
            log.info("Skipped DB schema version check due to SKIP_SCHEMA_VERSION_CHECK set to true!");
            return true;
        }
        boolean isOldSchema = true;
        try {
            Statement statement = conn.createStatement();
            statement.execute("CREATE TABLE IF NOT EXISTS tb_schema_settings ( schema_version bigint NOT NULL, CONSTRAINT tb_schema_settings_pkey PRIMARY KEY (schema_version));");
            Thread.sleep(1000);
            ResultSet resultSet = statement.executeQuery("SELECT schema_version FROM tb_schema_settings;");
            if (resultSet.next()) {
                isOldSchema = resultSet.getLong(1) <= fromVersion;
            } else {
                resultSet.close();
                statement.execute("INSERT INTO tb_schema_settings (schema_version) VALUES (" + fromVersion + ")");
            }
            statement.close();
        } catch (InterruptedException | SQLException e) {
            log.info("Failed to check current PostgreSQL schema due to: {}", e.getMessage());
        }
        return isOldSchema;
    }

    private static boolean getEnv(String name, boolean defaultValue) {
        String env = System.getenv(name);
        if (env == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(env);
    }

}
