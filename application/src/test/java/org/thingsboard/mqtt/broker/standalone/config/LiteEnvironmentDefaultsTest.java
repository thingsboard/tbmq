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
package org.thingsboard.mqtt.broker.standalone.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.commons.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LiteEnvironmentDefaultsTest {

    private static final String SOURCE_NAME = "tbmqLiteDefaults";
    private static final String WAITS_SOURCE_NAME = "tbmqLiteShutdownWaits";

    private static final String GRACEFUL_PROPERTY = "tbmq.graceful.shutdown.timeout.sec";
    private static final String GRACEFUL_ENV = "TBMQ_GRACEFUL_SHUTDOWN_TIMEOUT_SEC";

    @TempDir
    Path tmp;

    private String gracefulPropertyBefore;

    // the post-processor sets a JVM-wide system property: keep it from leaking between tests
    @BeforeEach
    void rememberGracefulProperty() {
        gracefulPropertyBefore = System.getProperty(GRACEFUL_PROPERTY);
        System.clearProperty(GRACEFUL_PROPERTY);
    }

    @AfterEach
    void restoreGracefulProperty() {
        if (gracefulPropertyBefore == null) {
            System.clearProperty(GRACEFUL_PROPERTY);
        } else {
            System.setProperty(GRACEFUL_PROPERTY, gracefulPropertyBefore);
        }
    }

    @Test
    void givenLiteEdition_whenServiceIdIsSet_thenFixedServiceIdWins() {
        var env = new MockEnvironment().withProperty("service.id", "host-42");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("service.id")).isEqualTo("tbmq-lite");
    }

    @Test
    void givenLiteEdition_whenStandaloneServiceIdIsSet_thenItOverridesTheDefault() {
        var env = new MockEnvironment()
                .withProperty("service.id", "host-42")
                .withProperty("standalone.service-id", "edge-7");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("service.id")).isEqualTo("edge-7");
    }

    @Test
    void givenLiteEdition_whenStandaloneServiceIdIsMissing_thenServiceIdIsTbmqLite() {
        var env = new MockEnvironment().withProperty("service.id", "host-42");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("service.id")).isEqualTo("tbmq-lite");
    }

    @Test
    void givenLiteEdition_whenStandaloneServiceIdIsEmpty_thenServiceIdIsTbmqLite() {
        var env = new MockEnvironment()
                .withProperty("service.id", "")
                .withProperty("standalone.service-id", "");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("service.id")).isEqualTo("tbmq-lite");
    }

    @Test
    void givenLiteEdition_whenStandaloneServiceIdIsWhitespace_thenServiceIdIsTbmqLite() {
        var env = new MockEnvironment().withProperty("standalone.service-id", "  \t ");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("service.id")).isEqualTo("tbmq-lite");
    }

    @Test
    void givenLiteEdition_whenStandaloneServiceIdIsAddedAfterPostProcessing_thenItIsStillResolved() {
        var env = new MockEnvironment();
        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        env.setProperty("standalone.service-id", "late-node");

        assertThat(env.getProperty("service.id")).isEqualTo("late-node");
    }

    @Test
    void givenLiteEdition_whenPostProcess_thenSourceHasHighestPrecedence() {
        var env = new MockEnvironment();

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getPropertySources().precedenceOf(env.getPropertySources().get(SOURCE_NAME))).isZero();
    }

    @Test
    void givenClusterEdition_whenPostProcess_thenEnvironmentIsLeftAlone() {
        var env = new MockEnvironment().withProperty("service.id", "host-42");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("cluster"));

        assertThat(env.getProperty("service.id")).isEqualTo("host-42");
        assertThat(env.getPropertySources().contains(SOURCE_NAME)).isFalse();
    }

    @Test
    void givenNoMarker_whenPostProcess_thenEnvironmentIsLeftAlone() {
        var env = new MockEnvironment().withProperty("service.id", "host-42");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker());

        assertThat(env.getProperty("service.id")).isEqualTo("host-42");
        assertThat(env.getPropertySources().contains(SOURCE_NAME)).isFalse();
    }

    @Test
    void givenLiteEdition_whenEnvironmentIsPrintedWithDebugOn_thenNoRecursion() {
        var env = new MockEnvironment();
        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));
        // with DEBUG on, the default PropertySource.toString prints its source
        Logger logger = (Logger) LoggerFactory.getLogger(env.getPropertySources().get(SOURCE_NAME).getClass());
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            assertThat(env.toString()).contains(SOURCE_NAME);
        } finally {
            logger.setLevel(previous);
        }
    }

    // Spring Boot's binder walks into a property source whose source is an environment, as into a nested one
    @Test
    void givenLiteEdition_whenSpringBootBindsFromTheEnvironment_thenTheDefaultsResolveWithoutRecursion() {
        var env = environmentWithBrokerYml();
        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));
        ConfigurationPropertySources.attach(env);

        Binder binder = Binder.get(env);

        assertThat(binder.bind("service.id", String.class).get()).isEqualTo("tbmq-lite");
        assertThat(binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void givenLiteEdition_whenNoEnvironmentVariablesAreSet_thenInheritedWaitsAreShortened() {
        var env = new MockEnvironment();

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("actors.system.disconnect-wait-timeout-ms")).isEqualTo("1500");
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("6s");
        for (String listener : new String[]{"tcp", "ssl", "ws", "wss"}) {
            assertThat(env.getProperty("listener." + listener + ".netty.shutdown_timeout")).as(listener).isEqualTo("1");
        }
    }

    @Test
    void givenLiteEdition_whenTheEnvironmentVariablesAreSet_thenTheUsersValuesWin() {
        var env = new MockEnvironment()
                .withProperty("ACTORS_SYSTEM_DISCONNECT_WAIT_TIMEOUT_MS", "9000")
                .withProperty("SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE", "30s")
                .withProperty("TCP_NETTY_SHUTDOWN_TIMEOUT", "7")
                .withProperty("SSL_NETTY_SHUTDOWN_TIMEOUT", "8")
                .withProperty("WS_NETTY_SHUTDOWN_TIMEOUT", "9")
                .withProperty("WSS_NETTY_SHUTDOWN_TIMEOUT", "10");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("actors.system.disconnect-wait-timeout-ms")).isEqualTo("9000");
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
        assertThat(env.getProperty("listener.tcp.netty.shutdown_timeout")).isEqualTo("7");
        assertThat(env.getProperty("listener.ssl.netty.shutdown_timeout")).isEqualTo("8");
        assertThat(env.getProperty("listener.ws.netty.shutdown_timeout")).isEqualTo("9");
        assertThat(env.getProperty("listener.wss.netty.shutdown_timeout")).isEqualTo("10");
    }

    @Test
    void givenLiteEdition_whenAnEnvironmentVariableIsBlank_thenTheLiteValueApplies() {
        var env = new MockEnvironment().withProperty("TCP_NETTY_SHUTDOWN_TIMEOUT", " ");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("listener.tcp.netty.shutdown_timeout")).isEqualTo("1");
    }

    @Test
    void givenClusterEdition_whenPostProcess_thenNoWaitIsShortenedAndNoSystemPropertyIsSet() {
        var env = new MockEnvironment();

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("cluster"));

        assertThat(env.getProperty("actors.system.disconnect-wait-timeout-ms")).isNull();
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isNull();
        assertThat(env.getProperty("listener.tcp.netty.shutdown_timeout")).isNull();
        assertThat(System.getProperty(GRACEFUL_PROPERTY)).isNull();
    }

    @Test
    void givenLiteEdition_whenNeitherGracefulTimeoutIsSet_thenSystemPropertyIsOne() {
        new LiteEnvironmentDefaults().postProcessEnvironment(new MockEnvironment(), appWithMarker("lite"));

        assertThat(System.getProperty(GRACEFUL_PROPERTY)).isEqualTo("1");
    }

    @Test
    void givenLiteEdition_whenGracefulSystemPropertyIsSet_thenItIsKept() {
        System.setProperty(GRACEFUL_PROPERTY, "12");

        new LiteEnvironmentDefaults().postProcessEnvironment(new MockEnvironment(), appWithMarker("lite"));

        assertThat(System.getProperty(GRACEFUL_PROPERTY)).isEqualTo("12");
    }

    @Test
    void givenLiteEdition_whenGracefulEnvironmentVariableIsSet_thenSystemPropertyIsNotSet() {
        var env = new MockEnvironment().withProperty(GRACEFUL_ENV, "20");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(System.getProperty(GRACEFUL_PROPERTY)).isNull();
    }

    @Test
    void givenLiteEdition_whenBrokerYmlIsLoaded_thenWaitsSourceSitsJustBeforeItsConfigDataSource() {
        var env = environmentWithBrokerYml();
        String ymlSource = brokerYmlSourceName(env);

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        List<String> names = env.getPropertySources().stream().map(PropertySource::getName).toList();
        assertThat(names.indexOf(WAITS_SOURCE_NAME)).isEqualTo(names.indexOf(ymlSource) - 1);
        assertThat(names.indexOf(SOURCE_NAME)).isZero();
    }

    @Test
    void givenLiteEdition_whenBrokerYmlIsLoadedAndNothingIsSet_thenLiteWaitsBeatTheFilesDefaults() {
        var env = environmentWithBrokerYml();
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("1m");

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("actors.system.disconnect-wait-timeout-ms")).isEqualTo("1500");
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("6s");
        assertThat(env.getProperty("listener.tcp.netty.shutdown_timeout")).isEqualTo("1");
        assertThat(env.getProperty("listener.ssl.netty.shutdown_timeout")).isEqualTo("1");
        assertThat(env.getProperty("listener.ws.netty.shutdown_timeout")).isEqualTo("1");
        assertThat(env.getProperty("listener.wss.netty.shutdown_timeout")).isEqualTo("1");
    }

    @Test
    void givenLiteEdition_whenASystemPropertyIsSet_thenItBeatsTheLiteWait() {
        var env = environmentWithBrokerYml();
        String key = "listener.tcp.netty.shutdown_timeout";
        System.setProperty(key, "3");
        try {
            new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

            assertThat(env.getProperty(key)).isEqualTo("3");
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void givenLiteEdition_whenACommandLineArgumentIsSet_thenItBeatsTheLiteWait() {
        var env = environmentWithBrokerYml();
        env.getPropertySources().addFirst(new SimpleCommandLinePropertySource("--spring.lifecycle.timeout-per-shutdown-phase=30s"));

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
    }

    @Test
    void givenLiteEdition_whenASystemEnvironmentVariableUsesTheRelaxedName_thenItBeatsTheLiteWait() {
        var env = environmentWithBrokerYml();
        env.getPropertySources().addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("LISTENER_TCP_NETTY_SHUTDOWN_TIMEOUT", "4")));

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("listener.tcp.netty.shutdown_timeout")).isEqualTo("4");
    }

    @Test
    void givenLiteEdition_whenAnAdditionalConfigSourceIsAboveTheYml_thenItBeatsTheLiteWait() {
        var env = environmentWithBrokerYml();
        String ymlSource = brokerYmlSourceName(env);
        // what a file under spring.config.additional-location becomes: a config-data source above the classpath one
        env.getPropertySources().addBefore(ymlSource, new MapPropertySource("Config resource 'file [/etc/tbmq/extra.yml]'",
                Map.of("actors.system.disconnect-wait-timeout-ms", "7000")));

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("actors.system.disconnect-wait-timeout-ms")).isEqualTo("7000");
    }

    @Test
    void givenLiteEdition_whenAnExternalFileHasTheYmlsName_thenItStaysAboveTheWaitsSource() {
        var env = environmentWithBrokerYml();
        String ymlSource = brokerYmlSourceName(env);
        env.getPropertySources().addBefore(ymlSource, new MapPropertySource(
                "Config resource 'file [/etc/tbmq/thingsboard-mqtt-broker.yml]' via location 'file:/etc/tbmq/'",
                Map.of("listener.ws.netty.shutdown_timeout", "9")));

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("listener.ws.netty.shutdown_timeout")).isEqualTo("9");
        assertThat(env.getProperty("listener.wss.netty.shutdown_timeout")).isEqualTo("1");
    }

    @Test
    void givenLiteEdition_whenBrokerYmlIsLoadedAndTheEnvironmentVariablesAreSet_thenTheUsersValuesWin() {
        var env = environmentWithBrokerYml();
        env.getPropertySources().addFirst(new MapPropertySource("vars", Map.of("SSL_NETTY_SHUTDOWN_TIMEOUT", "8")));

        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));

        assertThat(env.getProperty("listener.ssl.netty.shutdown_timeout")).isEqualTo("8");
    }

    @Test
    void givenLiteEdition_whenBrokerYmlIsLoaded_thenServiceIdStillBeatsTheYmlAndHonoursStandaloneServiceId() {
        var env = environmentWithBrokerYml();
        new LiteEnvironmentDefaults().postProcessEnvironment(env, appWithMarker("lite"));
        assertThat(env.getProperty("service.id")).isEqualTo("tbmq-lite");

        env.getPropertySources().addFirst(new MapPropertySource("args", Map.of("standalone.service-id", "edge-7")));

        assertThat(env.getProperty("service.id")).isEqualTo("edge-7");
    }

    @Test
    void givenLiteEditionAndNoConfigSourceForTheBrokerYml_whenPostProcess_thenTheFallbackIsLoggedOnceAtInfo() {
        Log log = mock(Log.class);

        new LiteEnvironmentDefaults(destination -> log).postProcessEnvironment(new MockEnvironment(), appWithMarker("lite"));

        // the waits source goes last, so the file's 15000 / 1m / 5 would win: say so instead of failing silently
        verify(log, times(1)).info(argThat(message -> message.toString().contains("shutdown waits are not applied")
                && message.toString().contains("thingsboard-mqtt-broker.yml")));
    }

    @Test
    void givenLiteEditionAndTheBrokerYmlSource_whenPostProcess_thenNothingIsLogged() {
        Log log = mock(Log.class);

        new LiteEnvironmentDefaults(destination -> log).postProcessEnvironment(environmentWithBrokerYml(), appWithMarker("lite"));

        verify(log, never()).info(any());
    }

    @Test
    void whenOrdered_thenItRunsAfterTheConfigDataPostProcessor() {
        assertThat(new LiteEnvironmentDefaults().getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
    }

    // the broker's yml loaded by Spring Boot's config-data loader, the way the application loads it
    private StandardEnvironment environmentWithBrokerYml() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("spring.config.name", "thingsboard-mqtt-broker")));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        env.getPropertySources().remove("test");
        return env;
    }

    private static String brokerYmlSourceName(StandardEnvironment env) {
        return env.getPropertySources().stream().map(PropertySource::getName)
                .filter(name -> name.startsWith("Config resource") && name.contains("class path resource [thingsboard-mqtt-broker.yml]"))
                .findFirst().orElseThrow();
    }

    // The resolver caches per class loader, so every test gets a fresh one.
    private SpringApplication appWithMarker(String... markers) {
        try {
            Path root = Files.createTempDirectory(tmp, "markers");
            URL[] urls = new URL[markers.length];
            for (int i = 0; i < markers.length; i++) {
                Path classpathRoot = root.resolve(String.valueOf(i));
                Path marker = classpathRoot.resolve("META-INF/tbmq-edition");
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, markers[i]);
                urls[i] = classpathRoot.toUri().toURL();
            }
            // the platform loader as parent keeps the markers of the real test classpath out of the lookup
            ClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            return new SpringApplication() {
                @Override
                public ClassLoader getClassLoader() {
                    return loader;
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
