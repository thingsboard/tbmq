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
package org.thingsboard.mqtt.broker.lite.durability;

import org.testcontainers.containers.PostgreSQLContainer;
import org.thingsboard.mqtt.broker.dao.PostgreSqlInitializer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The PostgreSQL of a broker that runs outside a Spring test context, where nothing creates the schema and the system
 * data. They are created the way the test suites create them, with {@link PostgreSqlInitializer#initDb}; the install
 * application is not used.
 */
public final class LiteTestDatabase implements AutoCloseable {

    private static final String IMAGE = "postgres:17";
    private static final String DATABASE = "thingsboard_mqtt_broker";

    private final PostgreSQLContainer<?> container;

    private LiteTestDatabase(PostgreSQLContainer<?> container) {
        this.container = container;
    }

    /**
     * Starts a container the caller owns and prepares its database.
     */
    public static LiteTestDatabase start() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(IMAGE).withDatabaseName(DATABASE);
        container.start();
        try {
            prepare(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        } catch (RuntimeException e) {
            container.stop();
            throw e;
        }
        return new LiteTestDatabase(container);
    }

    public String jdbcUrl() {
        return container.getJdbcUrl();
    }

    public String username() {
        return container.getUsername();
    }

    public String password() {
        return container.getPassword();
    }

    @Override
    public void close() {
        container.stop();
    }

    /**
     * Prepares an existing database for a broker started by hand, for example for the capacity runs. Whatever the
     * database holds is dropped first.
     *
     * @param args the JDBC URL, the user name and the password
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: " + LiteTestDatabase.class.getName() + " <jdbcUrl> <username> <password>");
            System.exit(2);
        }
        prepare(args[0], args[1], args[2]);
    }

    private static void prepare(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            PostgreSqlInitializer.initDb(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to prepare the database at " + jdbcUrl, e);
        }
    }

}
