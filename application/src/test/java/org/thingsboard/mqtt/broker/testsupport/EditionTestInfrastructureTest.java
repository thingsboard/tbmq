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
package org.thingsboard.mqtt.broker.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TestRule;
import org.thingsboard.mqtt.broker.testsupport.cluster.ClusterEditionTestInfrastructure;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditionTestInfrastructureTest {

    private static final String SERVICES_FILE = "META-INF/services/" + EditionTestInfrastructure.class.getName();

    @Test
    void exactlyTheClusterInfrastructureIsOnTheApplicationTestClasspath() {
        assertThat(ServiceLoader.load(EditionTestInfrastructure.class).stream().map(ServiceLoader.Provider::type))
                .containsExactly(ClusterEditionTestInfrastructure.class);
        assertThat(EditionTestInfrastructure.loadClassRule()).isNotNull();
    }

    @Test
    void givenNoProvider_whenLoadClassRule_thenFailsListingNoProviders() {
        ClassLoader withoutProviders = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return SERVICES_FILE.equals(name) ? Collections.emptyEnumeration() : super.getResources(name);
            }
        };

        assertThatThrownBy(() -> loadClassRuleWith(withoutProviders))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exactly one")
                .hasMessageContaining("found 0");
    }

    @Test
    void givenTwoProviders_whenLoadClassRule_thenFailsListingBothProviders(@TempDir Path tmp) throws IOException {
        Path servicesFile = tmp.resolve(SERVICES_FILE);
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(servicesFile, SecondProvider.class.getName() + "\n", StandardCharsets.UTF_8);
        try (URLClassLoader withTwoProviders = new URLClassLoader(new URL[]{tmp.toUri().toURL()}, getClass().getClassLoader())) {
            assertThatThrownBy(() -> loadClassRuleWith(withTwoProviders))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("found 2")
                    .hasMessageContaining(ClusterEditionTestInfrastructure.class.getName())
                    .hasMessageContaining(SecondProvider.class.getName());
        }
    }

    private static TestRule loadClassRuleWith(ClassLoader loader) {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return EditionTestInfrastructure.loadClassRule();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    public static class SecondProvider implements EditionTestInfrastructure {
        @Override
        public TestRule classRule() {
            return (base, description) -> base;
        }
    }

}
