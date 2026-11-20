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

import org.junit.rules.TestRule;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The edition-specific part of the shared integration-test base: the external services (and the system properties
 * pointing the application at them) that a test class needs while its Spring context is alive.
 * <p>
 * Implementations are discovered with {@link ServiceLoader}; the base class applies the one found as a JUnit 4
 * {@code @ClassRule}, so the services are started before the Spring context loads and stopped after it closes.
 */
public interface EditionTestInfrastructure {

    /**
     * @return the class-level rule that starts the infrastructure and publishes the system properties the application
     * reads, then reverts both when the test class is done
     */
    TestRule classRule();

    /**
     * @return the properties the Spring context of an {@link EditionTest} class gets on top of the shared
     * {@code application-test.properties}; empty by default
     */
    default Map<String, Object> contextProperties() {
        return Map.of();
    }

    /**
     * @return the entries of a properties file on the test classpath, e.g. for {@link #contextProperties()}
     */
    static Map<String, Object> classpathProperties(String resource) {
        try {
            Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource(resource));
            return properties.stringPropertyNames().stream()
                    .collect(Collectors.toUnmodifiableMap(Function.identity(), properties::getProperty));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource + " from the test classpath", e);
        }
    }

    /**
     * @return the class-level rule of the provider found by {@link #load()}
     */
    static TestRule loadClassRule() {
        return load().classRule();
    }

    /**
     * Exactly one provider must be on the test classpath ({@link ServiceLoader}); fails with the list otherwise.
     */
    static EditionTestInfrastructure load() {
        List<ServiceLoader.Provider<EditionTestInfrastructure>> providers =
                ServiceLoader.load(EditionTestInfrastructure.class).stream().toList();
        if (providers.size() != 1) {
            throw new IllegalStateException("Exactly one " + EditionTestInfrastructure.class.getName()
                    + " provider must be on the test classpath, but found " + providers.size() + ": "
                    + providers.stream().map(provider -> provider.type().getName()).toList());
        }
        return providers.get(0).get();
    }

}
