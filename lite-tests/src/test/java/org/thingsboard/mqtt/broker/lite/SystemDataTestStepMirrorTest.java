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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;
import org.thingsboard.mqtt.broker.testsupport.lite.SystemDataTestStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Keeps {@link SystemDataTestStep} a copy of {@code sql/system-data.sql}, through the golden file that the dao-sql module
 * pins to that SQL file ({@code system-data-rows.json}, read from the sibling module's directory, with no class path
 * between the modules). The step saves into an empty backend exactly the rows of the golden file, and only for the
 * four tables the file names, column by column. A JSON column is compared as a JSON tree, and the provider
 * configuration as the SQL DAO reads it, so formatting and key order do not count. A column that the golden file does
 * not name, or the test does not write, fails the test, so a schema change reaches it too.
 */
class SystemDataTestStepMirrorTest {

    // relative to the module directory, which is the working directory of the tests
    private static final String GOLDEN_FILE = "../dao-sql/src/test/resources/sql/system-data-rows.json";

    private final UserDao userDao = mock(UserDao.class);
    private final UserCredentialsDao userCredentialsDao = mock(UserCredentialsDao.class);
    private final AdminSettingsDao adminSettingsDao = mock(AdminSettingsDao.class);
    private final MqttAuthProviderDao mqttAuthProviderDao = mock(MqttAuthProviderDao.class);
    private final MqttClientCredentialsService mqttClientCredentialsService = mock(MqttClientCredentialsService.class);
    private final WebSocketConnectionDao webSocketConnectionDao = mock(WebSocketConnectionDao.class);
    // seededInThisStart() is false: the insert pass alone
    private final StoreSeedState storeSeedState = mock(StoreSeedState.class);

    @Test
    void givenAnEmptyBackend_whenApply_thenTheSavedRowsAreThoseOfTheGoldenFile() throws IOException {
        JsonNode golden = golden();

        new SystemDataTestStep(userDao, userCredentialsDao, adminSettingsDao, mqttAuthProviderDao,
                mqttClientCredentialsService, webSocketConnectionDao, storeSeedState, true).apply();

        assertThat(golden.fieldNames()).toIterable().as("tables of the golden file")
                .containsExactlyInAnyOrder("broker_user", "user_credentials", "admin_settings", "mqtt_auth_provider");
        assertSameRows(golden, "broker_user", saved(userDao, User.class), user -> row(
                "id", user.getId(),
                "created_time", user.getCreatedTime(),
                "email", user.getEmail(),
                "authority", user.getAuthority() == null ? null : user.getAuthority().name(),
                "first_name", user.getFirstName(),
                "last_name", user.getLastName(),
                "additional_info", user.getAdditionalInfo()));
        assertSameRows(golden, "user_credentials", saved(userCredentialsDao, UserCredentials.class), credentials -> row(
                "id", credentials.getId(),
                "created_time", credentials.getCreatedTime(),
                "user_id", credentials.getUserId(),
                "enabled", credentials.isEnabled(),
                "password", credentials.getPassword(),
                "activate_token", credentials.getActivateToken(),
                "reset_token", credentials.getResetToken()));
        assertSameRows(golden, "admin_settings", saved(adminSettingsDao, AdminSettings.class), settings -> row(
                "id", settings.getId(),
                "created_time", settings.getCreatedTime(),
                "key", settings.getKey(),
                "json_value", settings.getJsonValue()));
        assertSameRows(golden, "mqtt_auth_provider", saved(mqttAuthProviderDao, MqttAuthProvider.class), provider -> row(
                "id", provider.getId(),
                "created_time", provider.getCreatedTime(),
                "enabled", provider.isEnabled(),
                "type", provider.getType() == null ? null : provider.getType().name(),
                "configuration", provider.getConfiguration(),
                "additional_info", provider.getAdditionalInfo()));
    }

    /**
     * The rows of {@code table} in the golden file equal the saved entities as {@code toRow} writes them, ordered by id
     * as the golden file is.
     */
    private static <T> void assertSameRows(JsonNode golden, String table, List<T> saved, Function<T, ObjectNode> toRow) {
        JsonNode expected = golden.get(table);
        assertThat(expected).as("rows of %s in the golden file", table).isNotNull();
        assertThat(saved).as("entities the step saves into %s", table).isNotEmpty();
        List<ObjectNode> rows = saved.stream().map(toRow)
                .sorted(Comparator.comparing(row -> row.get("id").asText()))
                .toList();
        ArrayNode actual = JacksonUtil.newArrayNode();
        rows.forEach(actual::add);

        assertThat(columns(actual)).as("columns of %s the test compares", table).isEqualTo(columns(expected));
        // the entity's JSON written and read once, so that a number has the type that a parsed file gives it
        assertThat(JacksonUtil.toJsonNode(JacksonUtil.toString(actual))).as("rows of %s the step saves", table).isEqualTo(expected);
    }

    private static Set<String> columns(JsonNode rows) {
        Set<String> columns = new TreeSet<>();
        rows.forEach(row -> row.fieldNames().forEachRemaining(columns::add));
        return columns;
    }

    private static JsonNode golden() throws IOException {
        Path basedir = Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        Path file = basedir.resolve(GOLDEN_FILE).normalize();
        assertThat(file).as("the golden file of the system data, which the dao-sql module holds; this test reads it by path "
                + "from the lite-tests module directory (%s)", basedir).isRegularFile();
        return JacksonUtil.toJsonNode(Files.readString(file));
    }

    private static <T> List<T> saved(Object dao, Class<T> type) {
        return Mockito.mockingDetails(dao).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> type.cast(invocation.getArgument(0)))
                .toList();
    }

    private static ObjectNode row(Object... namesAndValues) {
        ObjectNode row = JacksonUtil.newObjectNode();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            row.set((String) namesAndValues[i], node(namesAndValues[i + 1]));
        }
        return row;
    }

    // a column value in the form of the golden file: ids as text, JSON as a tree, a null as a JSON null
    private static JsonNode node(Object value) {
        return switch (value) {
            case null -> JacksonUtil.OBJECT_MAPPER.nullNode();
            case UUID uuid -> JacksonUtil.OBJECT_MAPPER.getNodeFactory().textNode(uuid.toString());
            case JsonNode json -> json;
            // a scalar, or the provider configuration as the SQL DAO reads it from its column: the configuration type, as a tree
            default -> JacksonUtil.valueToTree(value);
        };
    }

}
