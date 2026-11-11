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
package org.thingsboard.mqtt.broker.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ThingsboardErrorResponseHandlerDatabaseErrorTest {

    private final ThingsboardErrorResponseHandler handler = new ThingsboardErrorResponseHandler();

    @Test
    void givenConstraintViolationInsideDataAccessException_whenHandle_thenDatabaseError500() throws Exception {
        var cause = new ConstraintViolationException("dup", new SQLException("dup"), "uk");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new DataIntegrityViolationException("dup", cause), response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("\"message\":\"Database error\"");
    }

    @Test
    void givenThingsboardDatabaseExceptionWrappingDataAccessException_whenHandle_thenDatabaseError500() throws Exception {
        var dataAccessException = new DataIntegrityViolationException("dup", new SQLException("dup"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new ThingsboardException(dataAccessException, ThingsboardErrorCode.DATABASE), response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("\"message\":\"Database error\"");
    }

}
