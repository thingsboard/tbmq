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
package org.thingsboard.mqtt.broker.common.data.security.jwt;

import org.junit.Test;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwksVerifierConfigurationTest {

    @Test
    public void shouldCallUrlValidation() {
        JwksVerifierConfiguration config = new JwksVerifierConfiguration();
        config.setEndpoint("http://valid-url");
        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    public void shouldThrowIfUrlIsInvalid() {
        JwksVerifierConfiguration config = new JwksVerifierConfiguration();
        config.setEndpoint("not-a-valid-url");
        assertThatThrownBy(config::validate)
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Invalid url: not-a-valid-url");
    }

}
