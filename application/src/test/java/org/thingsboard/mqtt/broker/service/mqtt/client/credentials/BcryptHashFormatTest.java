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
package org.thingsboard.mqtt.broker.service.mqtt.client.credentials;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptHashFormatTest {

    private static final String BODY = "N9qo8uLOickgx2ZMRZoMyeIjZAdg.Ee2Ca5UNh27zdnRDeR2.pgdK";

    @Test
    void acceptsTheThreePrefixesAndCostsFourToFourteen() {
        assertThat(BODY).hasSize(53);
        assertThat(BcryptHashFormat.isValid("$2a$10$" + BODY)).isTrue();
        assertThat(BcryptHashFormat.isValid("$2b$04$" + BODY)).isTrue();
        assertThat(BcryptHashFormat.isValid("$2y$14$" + BODY)).isTrue();
        assertThat(BcryptHashFormat.isValid("$2a$09$" + BODY)).isTrue();
    }

    @Test
    void acceptsAHashTheEncoderProduces() {
        assertThat(BcryptHashFormat.isValid(new BCryptPasswordEncoder().encode("pw"))).isTrue();
    }

    @Test
    void refusesOtherFormats() {
        assertThat(BcryptHashFormat.isValid("$2a$03$" + BODY)).isFalse();
        assertThat(BcryptHashFormat.isValid("$2a$15$" + BODY)).isFalse();
        assertThat(BcryptHashFormat.isValid("$2x$10$" + BODY)).isFalse();
        assertThat(BcryptHashFormat.isValid("$2a$10$" + BODY.substring(1))).isFalse();
        assertThat(BcryptHashFormat.isValid("$2a$10$" + BODY + "a")).isFalse();
        assertThat(BcryptHashFormat.isValid("$2a$10$" + BODY.substring(1) + "!")).isFalse();
        assertThat(BcryptHashFormat.isValid("$2a$10$" + BODY + "\n")).isFalse();
        assertThat(BcryptHashFormat.isValid("plaintext")).isFalse();
        assertThat(BcryptHashFormat.isValid("")).isFalse();
        assertThat(BcryptHashFormat.isValid(null)).isFalse();
    }

}
