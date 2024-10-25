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
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Profile("install")
@Slf4j
public class SqlDatabaseUpgradeService implements DatabaseEntitiesUpgradeService {

    private static final String THINGSBOARD_WINDOWS_UPGRADE_DIR = "THINGSBOARD_WINDOWS_UPGRADE_DIR";
    private static final String PATH_TO_USERS_PUBLIC_FOLDER = "C:\\Users\\Public";
    private static final String SCHEMA_UPDATE_SQL = "schema_update.sql";

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUserName;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit}")
    private int messagesLimit;

    @Autowired
    private InstallScripts installScripts;

    @Autowired
    private DeviceMsgService deviceMsgService;

    @Override
    public void upgradeDatabase(String fromVersion) {
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
                updateSchema("1.3.0", 1003000, "1.4.0", 1004000);
                break;
            case "1.4.0":
                updateSchema("1.4.0", 1004000, "2.0.0", 2000000);
                Path pathToTempFile = getTempFile("device_publish_msgs", ".csv");
                Pattern pattern = Pattern.compile("jdbc:postgresql://([^:/]+):(\\d+)/(\\w+)");
                Matcher matcher = pattern.matcher(dbUrl);
                if (!matcher.find() || matcher.groupCount() != 3) {
                    throw new RuntimeException("Failed to extract db host, port, and name from SPRING_DATASOURCE_URL environment variable! " +
                                               "Ensure the URL is in the format: jdbc:postgresql://<host>:<port>/<database>. " +
                                               "Example: jdbc:postgresql://localhost:5432/thingsboard_mqtt_broker");
                }
                String host = matcher.group(1);
                String port = matcher.group(2);
                String dbName = matcher.group(3);
                String command = String.format(
                        "psql -h %s -p %s -U %s -d %s -c \"\\COPY (" +
                        "SELECT dpm.client_id, " +
                        "       dpm.topic, " +
                        "       dpm.time, " +
                        "       dpm.packet_id, " +
                        "       dpm.packet_type, " +
                        "       dpm.qos, " +
                        "       dpm.payload, " +
                        "       dpm.user_properties, " +
                        "       dpm.retain, " +
                        "       dpm.msg_expiry_interval, " +
                        "       dpm.payload_format_indicator, " +
                        "       dpm.content_type, " +
                        "       dpm.response_topic, " +
                        "       dpm.correlation_data " +
                        "FROM device_publish_msg dpm " +
                        "JOIN device_session_ctx dsc ON dpm.client_id = dsc.client_id " +
                        "WHERE dpm.serial_number >= (dsc.last_serial_number - %d + 1) " +
                        "ORDER BY dpm.client_id, dpm.serial_number ASC) " +
                        "TO '%s' WITH CSV HEADER\"",
                        host, port, dbUserName, dbName, messagesLimit, pathToTempFile);
                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
                processBuilder.environment().put("PGPASSWORD", dbPassword);
                try {
                    Process process = processBuilder.start();
                    StringBuilder commandExecutionErrorLogs = new StringBuilder();
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            commandExecutionErrorLogs.append(line).append(System.lineSeparator());
                        }
                    }
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("Failed to copy device publish messages to CSV file: " + pathToTempFile +
                                                   ". psql \\copy command failed with exit code: " + exitCode + System.lineSeparator() +
                                                   "Command execution errors: " + System.lineSeparator() + commandExecutionErrorLogs);
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Failed to copy device publish messages to csv file: " + pathToTempFile + " due to: ", e);
                }
                if (!pathToTempFile.toFile().exists()) {
                    break;
                }
                boolean migrated = false;
                try {
                    log.info("Starting migration of device publish messages to Redis ...");
                    deviceMsgService.importFromCsvFile(pathToTempFile);
                    migrated = true;
                    log.info("Successfully migrated device publish messages to Redis!");
                } catch (Exception e) {
                    log.error("Failed to import device publish messages from csv file: {}", pathToTempFile, e);
                }
                if (migrated) {
                    try (Connection conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword)) {
                        log.info("Removing device publish messages from Postgres ...");
                        conn.createStatement().execute("DROP TABLE IF EXISTS device_publish_msg;");
                        conn.createStatement().execute("DROP TABLE IF EXISTS device_session_ctx;");
                        conn.createStatement().execute("DROP PROCEDURE IF EXISTS export_device_publish_msgs;");
                        log.info("Successfully removed device publish messages from Postgres!");
                    } catch (Exception e) {
                        log.warn("Failed to remove device publish messages from Postgres due to: ", e);
                    }
                }
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
        if (getBoolEnv("SKIP_SCHEMA_VERSION_CHECK", false)) {
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

    private Path getTempFile(String fileName, String extension) {
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                String pathStr = getStrEnv(THINGSBOARD_WINDOWS_UPGRADE_DIR, PATH_TO_USERS_PUBLIC_FOLDER);
                Path pathToPublicFolder = Paths.get(pathStr);
                return Files.createTempFile(pathToPublicFolder, fileName, extension).toAbsolutePath();
            }
            Path tempDirPath = Files.createTempDirectory(fileName);
            File tempDirAsFile = tempDirPath.toFile();
            boolean writable = tempDirAsFile.setWritable(true, false);
            boolean readable = tempDirAsFile.setReadable(true, false);
            boolean executable = tempDirAsFile.setExecutable(true, false);
            boolean permissionsGranted = writable && readable && executable;
            if (!permissionsGranted) {
                throw new RuntimeException("Failed to grant write permissions for the: " + tempDirPath + " directory!");
            }
            return tempDirPath.resolve(fileName + extension).toAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp file due to: ", e);
        }
    }

    private static boolean getBoolEnv(String name, boolean defaultValue) {
        String env = System.getenv(name);
        if (env == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(env);
    }

    private static String getStrEnv(String name, String defaultValue) {
        String env = System.getenv(name);
        return env == null ? defaultValue : env;
    }

}
