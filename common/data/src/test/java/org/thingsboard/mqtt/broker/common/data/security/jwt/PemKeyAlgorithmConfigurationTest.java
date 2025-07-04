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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.util.SslUtil;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.NamedParameterSpec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
public class PemKeyAlgorithmConfigurationTest {

    @Test
    public void shouldThrowWhenPublicPemKeyFileNameIsBlank() {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("   ");
        config.setPublicPemKey("fake-key");

        assertThatThrownBy(config::validate)
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Public PEM key file name should be specified!");
    }

    @Test
    public void shouldThrowWhenPublicPemKeyIsBlank() {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("key.pem");
        config.setPublicPemKey("   ");

        assertThatThrownBy(config::validate)
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Public PEM key should be specified!");
    }

    @Test
    public void shouldThrowWhenKeyTypeIsUnsupported() {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("key.pem");
        config.setPublicPemKey("valid-key");

        PublicKey mockKey = mock(DSAPublicKey.class);
        try (MockedStatic<SslUtil> mocked = mockStatic(SslUtil.class)) {
            mocked.when(() -> SslUtil.readPublicKey("valid-key")).thenReturn(mockKey);
            assertThatThrownBy(config::validate)
                    .isInstanceOf(DataValidationException.class)
                    .hasMessageContaining("Only RSA, EC, or Ed25519 public keys are supported");
        }
    }

    @Test
    public void shouldPassForRsaKey() {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("key.pem");
        config.setPublicPemKey("valid-rsa-key");

        RSAPublicKey rsaKey = mock(RSAPublicKey.class);
        try (MockedStatic<SslUtil> mocked = mockStatic(SslUtil.class)) {
            mocked.when(() -> SslUtil.readPublicKey("valid-rsa-key")).thenReturn(rsaKey);
            assertThatCode(config::validate).doesNotThrowAnyException();
        }
    }

    @Test
    public void shouldPassForEcKey() {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("key.pem");
        config.setPublicPemKey("valid-ec-key");

        ECPublicKey ecKey = mock(ECPublicKey.class);
        try (MockedStatic<SslUtil> mocked = mockStatic(SslUtil.class)) {
            mocked.when(() -> SslUtil.readPublicKey("valid-ec-key")).thenReturn(ecKey);
            assertThatCode(config::validate).doesNotThrowAnyException();
        }
    }

    @Test
    public void shouldPassForEd25519Key() {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("key.pem");
        config.setPublicPemKey("valid-ed25519-key");

        EdECPublicKey edKey = mock(EdECPublicKey.class);
        NamedParameterSpec paramSpec = mock(NamedParameterSpec.class);
        given(paramSpec.getName()).willReturn("Ed25519");
        given(edKey.getParams()).willReturn(paramSpec);

        try (MockedStatic<SslUtil> mocked = mockStatic(SslUtil.class)) {
            mocked.when(() -> SslUtil.readPublicKey("valid-ed25519-key")).thenReturn(edKey);
            assertThatCode(config::validate).doesNotThrowAnyException();
        }
    }

    @Test
    public void shouldThrowIfKeyParsingFails() throws Exception {
        PemKeyAlgorithmConfiguration config = new PemKeyAlgorithmConfiguration();
        config.setPublicPemKeyFileName("key.pem");
        config.setPublicPemKey("bad-key");

        assertThatThrownBy(config::validate)
                .isInstanceOf(DataValidationException.class)
                .hasMessageStartingWith("Failed to parse public PEM key: ");
    }
}
