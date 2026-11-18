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
package org.thingsboard.mqtt.broker.dao.exception;

import org.junit.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityConstraintViolationExceptionTest {

    @Test
    public void findsTheViolationThreeCausesDeep() {
        var v = new EntityConstraintViolationException("name_unq_key", new RuntimeException("x"));
        assertThat(EntityConstraintViolationException.find(new RuntimeException(new IllegalStateException(v)))).contains(v);
    }

    @Test
    public void isViolationOfIgnoresCaseAndOtherNames() {
        var t = new RuntimeException(new EntityConstraintViolationException("NAME_UNQ_KEY", null));
        assertThat(EntityConstraintViolationException.isViolationOf(t, "name_unq_key")).isTrue();
        assertThat(EntityConstraintViolationException.isViolationOf(t, "mqtt_auth_provider_type_key")).isFalse();
    }

    @Test
    public void itIsStillADataAccessException() {
        assertThat(new EntityConstraintViolationException("x", null)).isInstanceOf(DataAccessException.class);
    }

    @Test
    public void messageIsTheCausesMessageOrADefaultWithoutCause() {
        assertThat(new EntityConstraintViolationException("x", new RuntimeException("dup"))).hasMessage("dup");
        assertThat(new EntityConstraintViolationException("x", null)).hasMessage("Unique constraint violated: x");
    }

}
