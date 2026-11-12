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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LiteEnvironmentDefaultsTest {

    private static final String SOURCE_NAME = "tbmqLiteDefaults";

    @TempDir
    Path tmp;

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
