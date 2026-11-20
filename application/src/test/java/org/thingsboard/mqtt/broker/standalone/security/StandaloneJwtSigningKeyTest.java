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
package org.thingsboard.mqtt.broker.standalone.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.thingsboard.mqtt.broker.config.JwtSettings;
import org.thingsboard.mqtt.broker.dao.store.StoreSecrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StandaloneJwtSigningKeyTest {

    private static final String BROKER_CONFIG = "thingsboard-mqtt-broker.yml";

    private final byte[] storeKey = new byte[64];
    private final StoreSecrets storeSecrets = mock(StoreSecrets.class);
    private final JwtSettings settings = new JwtSettings();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(StandaloneJwtSigningKey.class);

    @BeforeEach
    void setUp() {
        new Random(7).nextBytes(storeKey);
        when(storeSecrets.jwtSigningKey()).thenReturn(storeKey);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    void aValueEqualToTheYmlDefaultIsReplacedByTheStoreKey() {
        settings.setTokenSigningKey(StandaloneJwtSigningKey.YML_DEFAULT);

        new StandaloneJwtSigningKey(settings, storeSecrets).init();

        assertThat(settings.getTokenSigningKey()).isEqualTo(Base64.getEncoder().encodeToString(storeKey));
        assertThat(Base64.getDecoder().decode(settings.getTokenSigningKey())).hasSize(64);
        assertThat(allLogged()).doesNotContain(settings.getTokenSigningKey());
    }

    @Test
    void anOperatorValueWins() {
        String operatorKey = Base64.getEncoder().encodeToString("an-operator-chosen-signing-key-of-sufficient-length-for-hs512!".getBytes());
        settings.setTokenSigningKey(operatorKey);

        new StandaloneJwtSigningKey(settings, storeSecrets).init();

        assertThat(settings.getTokenSigningKey()).isEqualTo(operatorKey);
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("security.jwt.tokenSigningKey");
        });
        assertThat(allLogged()).doesNotContain(operatorKey);
    }

    @Test
    void aBlankValueIsNotAnOperatorValueAndTheStoreKeyIsUsed() {
        settings.setTokenSigningKey(" ");

        new StandaloneJwtSigningKey(settings, storeSecrets).init();

        assertThat(settings.getTokenSigningKey()).isEqualTo(Base64.getEncoder().encodeToString(storeKey));
    }

    @Test
    void theSettingsToStringDoesNotPrintTheKey() {
        settings.setTokenSigningKey("a-secret-signing-key");
        settings.setTokenIssuer("issuer");

        assertThat(settings.toString()).doesNotContain("a-secret-signing-key").contains("issuer");
    }

    @Test
    void theYmlDefaultConstantMatchesTheYml() {
        PropertySource<?> source = loadBrokerYml();

        // the constant is what the class compares with: it has to follow the default in the yml
        assertThat(source.getProperty("security.jwt.tokenSigningKey"))
                .isEqualTo("${JWT_TOKEN_SIGNING_KEY:" + StandaloneJwtSigningKey.YML_DEFAULT + "}");
    }

    private String allLogged() {
        return String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    private static PropertySource<?> loadBrokerYml() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(BROKER_CONFIG, new ClassPathResource(BROKER_CONFIG));
            return sources.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
