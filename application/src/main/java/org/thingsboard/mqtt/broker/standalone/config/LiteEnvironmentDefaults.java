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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;

import java.util.Map;
import java.util.function.Function;

/**
 * Adds the fixed settings of the lite edition as the highest-precedence property source. A value is either a
 * constant or a {@code Function<Environment, Object>} evaluated on every lookup, so a setting the user may still
 * choose (for example {@code standalone.service-id}) applies whether it is loaded before or after this source.
 * The cluster edition, and a classpath without an edition marker, are left untouched.
 */
public class LiteEnvironmentDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "tbmqLiteDefaults";

    static final String DEFAULT_SERVICE_ID = "tbmq-lite";

    static Map<String, Object> defaults() {
        Function<Environment, Object> serviceId = env -> {
            // a blank value (an empty STANDALONE_SERVICE_ID) must not fall through to the hostname
            String configured = env.getProperty("standalone.service-id");
            return configured == null || configured.isBlank() ? DEFAULT_SERVICE_ID : configured;
        };
        return Map.of("service.id", serviceId);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TbmqEdition edition;
        try {
            edition = TbmqEditionResolver.resolve(application.getClassLoader());
        } catch (IllegalStateException e) {
            // no or conflicting markers: the edition condition reports it when the context starts
            return;
        }
        if (edition == TbmqEdition.LITE) {
            environment.getPropertySources().addFirst(new LazyDefaultsPropertySource(environment, defaults()));
        }
    }

    private static final class LazyDefaultsPropertySource extends EnumerablePropertySource<Environment> {

        private final Map<String, Object> values;

        private LazyDefaultsPropertySource(Environment environment, Map<String, Object> values) {
            super(PROPERTY_SOURCE_NAME, environment);
            this.values = values;
        }

        @Override
        public String[] getPropertyNames() {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object getProperty(String name) {
            Object value = values.get(name);
            return value instanceof Function<?, ?> function ? ((Function<Environment, Object>) function).apply(source) : value;
        }
    }

}
