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
package org.thingsboard.mqtt.broker.lite;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.lite.durability.LiteTestDatabase;
import org.thingsboard.mqtt.broker.testsupport.lite.SystemDataTestStep;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Keeps {@link SystemDataTestStep} a copy of {@code sql/system-data.sql}: in a fresh database that the SQL file
 * filled, the SQL file fills only the four tables the step covers, and each holds exactly the rows the step saves into
 * an empty backend, column by column. A JSON column is compared as the domain object the SQL DAO reads from it, so
 * formatting and key order do not count. A column the comparison does not name fails the test, so a schema change
 * reaches it too.
 */
class SystemDataTestStepMirrorTest {

    private static LiteTestDatabase db;

    private final UserDao userDao = mock(UserDao.class);
    private final UserCredentialsDao userCredentialsDao = mock(UserCredentialsDao.class);
    private final AdminSettingsDao adminSettingsDao = mock(AdminSettingsDao.class);
    private final MqttAuthProviderDao mqttAuthProviderDao = mock(MqttAuthProviderDao.class);

    @BeforeAll
    static void startDatabase() {
        db = LiteTestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void givenAnEmptyBackend_whenApply_thenTheSavedRowsAreThoseOfTheSqlFile() throws SQLException {
        new SystemDataTestStep(userDao, userCredentialsDao, adminSettingsDao, mqttAuthProviderDao, true).apply();

        try (Connection connection = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password())) {
            // the schema files insert nothing, so every filled table comes from the SQL file
            assertThat(nonEmptyTables(connection)).as("tables the SQL file fills")
                    .containsExactlyInAnyOrder("broker_user", "user_credentials", "admin_settings", "mqtt_auth_provider");
            assertSameRows(connection, "broker_user", saved(userDao, User.class), user -> row(
                    "id", user.getId(),
                    "created_time", user.getCreatedTime(),
                    "email", user.getEmail(),
                    "authority", user.getAuthority() == null ? null : user.getAuthority().name(),
                    "first_name", user.getFirstName(),
                    "last_name", user.getLastName(),
                    "additional_info", user.getAdditionalInfo()),
                    Map.of("additional_info", SystemDataTestStepMirrorTest::json));
            assertSameRows(connection, "user_credentials", saved(userCredentialsDao, UserCredentials.class), credentials -> row(
                    "id", credentials.getId(),
                    "created_time", credentials.getCreatedTime(),
                    "user_id", credentials.getUserId(),
                    "enabled", credentials.isEnabled(),
                    "password", credentials.getPassword(),
                    "activate_token", credentials.getActivateToken(),
                    "reset_token", credentials.getResetToken()),
                    Map.of());
            assertSameRows(connection, "admin_settings", saved(adminSettingsDao, AdminSettings.class), settings -> row(
                    "id", settings.getId(),
                    "created_time", settings.getCreatedTime(),
                    "key", settings.getKey(),
                    "json_value", settings.getJsonValue()),
                    Map.of("json_value", SystemDataTestStepMirrorTest::json));
            assertSameRows(connection, "mqtt_auth_provider", saved(mqttAuthProviderDao, MqttAuthProvider.class), provider -> row(
                    "id", provider.getId(),
                    "created_time", provider.getCreatedTime(),
                    "enabled", provider.isEnabled(),
                    "type", provider.getType() == null ? null : provider.getType().name(),
                    "configuration", JacksonUtil.valueToTree(provider.getConfiguration()),
                    "additional_info", provider.getAdditionalInfo()),
                    Map.of("configuration", SystemDataTestStepMirrorTest::providerConfiguration,
                            "additional_info", SystemDataTestStepMirrorTest::json));
        }
    }

    /**
     * The rows of {@code table} equal the saved entities as {@code toRow} writes them, after {@code readers} turned the
     * text of the named columns into what the entities hold.
     */
    private static <T> void assertSameRows(Connection connection, String table, List<T> saved, Function<T, Map<String, Object>> toRow,
                                           Map<String, UnaryOperator<Object>> readers) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> columns = new TreeSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = result.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnName(i));
            }
            while (result.next()) {
                Map<String, Object> row = new HashMap<>();
                for (String column : columns) {
                    Object value = readers.containsKey(column) ? readers.get(column).apply(result.getString(column)) : result.getObject(column);
                    row.put(column, value);
                }
                rows.add(row);
            }
        }
        List<Map<String, Object>> savedRows = saved.stream().map(toRow).toList();

        assertThat(rows).as("rows of %s", table).isNotEmpty();
        assertThat(new TreeSet<>(savedRows.getFirst().keySet())).as("columns of %s the test compares", table).isEqualTo(columns);
        assertThat(savedRows).as("rows of %s the step saves", table).containsExactlyInAnyOrderElementsOf(rows);
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

    private static <T> List<T> saved(Object dao, Class<T> type) {
        return Mockito.mockingDetails(dao).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> type.cast(invocation.getArgument(0)))
                .toList();
    }

    private static Map<String, Object> row(Object... namesAndValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            row.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return row;
    }

    private static Object json(Object text) {
        return text == null ? null : JacksonUtil.toJsonNode((String) text);
    }

    // as the SQL DAO reads it: into the configuration type, here back to a tree so both sides compare alike
    private static Object providerConfiguration(Object text) {
        if (text == null) {
            return null;
        }
        return JacksonUtil.valueToTree(JacksonUtil.fromString((String) text, MqttAuthProviderConfiguration.class));
    }

}
