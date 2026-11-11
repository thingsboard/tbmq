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
package org.thingsboard.mqtt.broker.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationStatusCheckerTest {

    private static final String MESSAGE = "Couldn't start database connection.";

    private DataSource dataSource;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void givenValidConnection_whenInit_thenNoExceptionAndConnectionClosed() throws SQLException {
        when(connection.isValid(5)).thenReturn(true);

        assertThatCode(() -> new ApplicationStatusChecker(providerOf(dataSource)).init()).doesNotThrowAnyException();

        verify(connection).close();
    }

    @Test
    void givenInvalidConnection_whenInit_thenThrowAndConnectionClosed() throws SQLException {
        when(connection.isValid(5)).thenReturn(false);

        assertThatThrownBy(() -> new ApplicationStatusChecker(providerOf(dataSource)).init())
                .isInstanceOf(RuntimeException.class)
                .hasMessage(MESSAGE);

        verify(connection).close();
    }

    @Test
    void givenConnectionFails_whenInit_thenThrowWithSqlCause() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));

        assertThatThrownBy(() -> new ApplicationStatusChecker(providerOf(dataSource)).init())
                .hasMessage(MESSAGE)
                .hasRootCauseMessage("down");
    }

    @Test
    void givenIsValidFails_whenInit_thenThrowWithSqlCauseAndConnectionClosed() throws SQLException {
        when(connection.isValid(5)).thenThrow(new SQLException("broken"));

        assertThatThrownBy(() -> new ApplicationStatusChecker(providerOf(dataSource)).init())
                .hasMessage(MESSAGE)
                .hasRootCauseMessage("broken");

        verify(connection).close();
    }

    @Test
    void givenNoDataSource_whenInit_thenNothingChecked() {
        assertThatCode(() -> new ApplicationStatusChecker(providerOf(null)).init()).doesNotThrowAnyException();
    }

    private static ObjectProvider<DataSource> providerOf(DataSource dataSource) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (dataSource != null) {
            beanFactory.addBean("dataSource", dataSource);
        }
        return beanFactory.getBeanProvider(DataSource.class);
    }

}
