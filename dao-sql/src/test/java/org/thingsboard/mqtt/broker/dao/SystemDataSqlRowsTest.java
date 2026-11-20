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
package org.thingsboard.mqtt.broker.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what {@code sql/system-data.sql} fills into a fresh database to the golden file {@code sql/system-data-rows.json}:
 * the SQL file fills only the four tables of the file, and every row and every column equals the golden file. The
 * management-data step of the standalone tests is compared with the same golden file, on the other side of the module
 * boundary, so the two cannot drift apart.
 * <p>
 * A column the golden file does not name fails the test, so a schema change reaches it. The golden file holds each
 * table's rows ordered by id, with every column of the row, a JSON column as a JSON tree, and the provider
 * configuration as the SQL DAO reads it, that is, through {@link MqttAuthProviderConfiguration}. After an intended
 * change of the SQL file or the schema, {@code -DsystemDataRows.regenerate=true} rewrites the golden file from the
 * database; that run only writes the file and compares nothing.
 */
class SystemDataSqlRowsTest {

    private static final String IMAGE = "postgres:17";
    private static final String DATABASE = "thingsboard_mqtt_broker";
    private static final String REGENERATE = "systemDataRows.regenerate";
    private static final Path GOLDEN_FILE = Path.of("src/test/resources/sql/system-data-rows.json");
    private static final String GOLDEN_RESOURCE = "sql/system-data-rows.json";

    private static final List<String> TABLES = List.of("admin_settings", "broker_user", "mqtt_auth_provider", "user_credentials");
    // text columns that hold JSON
    private static final Set<String> JSON_COLUMNS = Set.of("additional_info", "json_value");
    private static final String PROVIDER_CONFIGURATION_COLUMN = "configuration";

    private static PostgreSQLContainer<?> container;

    @BeforeAll
    static void startDatabase() throws SQLException {
        container = new PostgreSQLContainer<>(IMAGE).withDatabaseName(DATABASE);
        container.start();
        try (Connection connection = container.createConnection("")) {
            PostgreSqlInitializer.initDb(connection);
        }
    }

    @AfterAll
    static void stopDatabase() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void givenAFreshDatabase_whenSystemDataIsLoaded_thenOnlyTheGoldenTablesAreFilled() throws SQLException {
        try (Connection connection = container.createConnection("")) {
            // the schema files insert nothing, so every filled table comes from the SQL file
            assertThat(nonEmptyTables(connection)).as("tables the SQL file fills").containsExactlyInAnyOrderElementsOf(TABLES);
        }
    }

    @Test
    void givenAFreshDatabase_whenSystemDataIsLoaded_thenEveryRowAndColumnEqualsTheGoldenFile() throws SQLException, IOException {
        ObjectNode actual = JacksonUtil.newObjectNode();
        try (Connection connection = container.createConnection("")) {
            for (String table : TABLES) {
                actual.set(table, rows(connection, table));
            }
        }
        if (Boolean.getBoolean(REGENERATE)) {
            Files.writeString(GOLDEN_FILE, JacksonUtil.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n",
                    StandardCharsets.UTF_8);
            return;
        }

        JsonNode golden = golden();
        assertThat(golden.fieldNames()).toIterable().as("tables of the golden file").containsExactlyElementsOf(TABLES);
        for (String table : TABLES) {
            assertThat(actual.get(table)).as("rows of %s", table).isEqualTo(golden.get(table));
        }
    }

    private static JsonNode golden() throws IOException {
        try (var stream = SystemDataSqlRowsTest.class.getClassLoader().getResourceAsStream(GOLDEN_RESOURCE)) {
            assertThat(stream).as("%s on the test classpath", GOLDEN_RESOURCE).isNotNull();
            return JacksonUtil.OBJECT_MAPPER.readTree(stream);
        }
    }

    private static ArrayNode rows(Connection connection, String table) throws SQLException {
        List<ObjectNode> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = result.getMetaData();
            Set<String> columns = new TreeSet<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnName(i));
            }
            while (result.next()) {
                ObjectNode row = JacksonUtil.newObjectNode();
                for (String column : columns) {
                    row.set(column, value(table, column, result));
                }
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(row -> row.get("id").asText()));
        ArrayNode array = JacksonUtil.newArrayNode();
        rows.forEach(array::add);
        return array;
    }

    private static JsonNode value(String table, String column, ResultSet result) throws SQLException {
        if (JSON_COLUMNS.contains(column)) {
            String text = result.getString(column);
            return text == null ? JacksonUtil.OBJECT_MAPPER.nullNode() : JacksonUtil.toJsonNode(text);
        }
        if (PROVIDER_CONFIGURATION_COLUMN.equals(column)) {
            String text = result.getString(column);
            return text == null ? JacksonUtil.OBJECT_MAPPER.nullNode()
                    : JacksonUtil.valueToTree(JacksonUtil.fromString(text, MqttAuthProviderConfiguration.class));
        }
        Object value = result.getObject(column);
        return switch (value) {
            case null -> JacksonUtil.OBJECT_MAPPER.nullNode();
            case UUID uuid -> JacksonUtil.OBJECT_MAPPER.getNodeFactory().textNode(uuid.toString());
            case String string -> JacksonUtil.OBJECT_MAPPER.getNodeFactory().textNode(string);
            case Long number -> JacksonUtil.OBJECT_MAPPER.getNodeFactory().numberNode(number);
            case Boolean bool -> JacksonUtil.OBJECT_MAPPER.getNodeFactory().booleanNode(bool);
            default -> throw new IllegalStateException(table + "." + column + " holds a " + value.getClass().getName()
                    + ", which the golden file has no form for");
        };
    }

    private static Set<String> nonEmptyTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        Set<String> nonEmpty = new TreeSet<>();
        for (String table : tables) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM \"" + table + "\")")) {
                if (result.next() && result.getBoolean(1)) {
                    nonEmpty.add(table);
                }
            }
        }
        return nonEmpty;
    }

}
