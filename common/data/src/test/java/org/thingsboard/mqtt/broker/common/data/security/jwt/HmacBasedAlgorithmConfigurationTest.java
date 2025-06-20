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

public class HmacBasedAlgorithmConfigurationTest {

    @Test
    public void shouldThrowWhenSecretIsBlank() {
        HmacBasedAlgorithmConfiguration config = new HmacBasedAlgorithmConfiguration();
        config.setSecret("   ");

        assertThatThrownBy(config::validate)
            .isInstanceOf(DataValidationException.class)
            .hasMessage("Secret should be specified!");
    }

    @Test
    public void shouldThrowWhenSecretIsNull() {
        HmacBasedAlgorithmConfiguration config = new HmacBasedAlgorithmConfiguration();
        config.setSecret(null);

        assertThatThrownBy(config::validate)
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Secret should be specified!");
    }

    @Test
    public void shouldThrowWhenSecretIsTooShort() {
        HmacBasedAlgorithmConfiguration config = new HmacBasedAlgorithmConfiguration();
        config.setSecret("short-secret"); // < 32 bytes

        assertThatThrownBy(config::validate)
            .isInstanceOf(DataValidationException.class)
            .hasMessage("HMAC secret is too short! Must be at least 32 bytes.");
    }

    @Test
    public void shouldPassWhenSecretLengthIsExactly32Bytes() {
        HmacBasedAlgorithmConfiguration config = new HmacBasedAlgorithmConfiguration();
        config.setSecret("12345678901234567890123456789012"); // 32 bytes

        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    public void shouldPassWhenSecretIsLongerThan32Bytes() {
        HmacBasedAlgorithmConfiguration config = new HmacBasedAlgorithmConfiguration();
        config.setSecret("this-is-a-very-long-and-secure-hmac-secret");

        assertThatCode(config::validate).doesNotThrowAnyException();
    }

}
