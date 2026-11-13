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

import java.util.HashMap;
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

    static final String GRACEFUL_SHUTDOWN_PROPERTY = "tbmq.graceful.shutdown.timeout.sec";
    static final String GRACEFUL_SHUTDOWN_ENV = "TBMQ_GRACEFUL_SHUTDOWN_TIMEOUT_SEC";
    static final String LITE_GRACEFUL_SHUTDOWN_SEC = "1";

    static Map<String, Object> defaults() {
        Function<Environment, Object> serviceId = env -> {
            // a blank value (an empty STANDALONE_SERVICE_ID) must not fall through to the hostname
            String configured = env.getProperty("standalone.service-id");
            return configured == null || configured.isBlank() ? DEFAULT_SERVICE_ID : configured;
        };
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("service.id", serviceId);
        // each wait keeps the environment variable the key already has in thingsboard-mqtt-broker.yml winning
        defaults.put("actors.system.disconnect-wait-timeout-ms", envOrDefault("ACTORS_SYSTEM_DISCONNECT_WAIT_TIMEOUT_MS", "1500"));
        defaults.put("spring.lifecycle.timeout-per-shutdown-phase", envOrDefault("SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE", "6s"));
        defaults.put("listener.tcp.netty.shutdown_timeout", envOrDefault("TCP_NETTY_SHUTDOWN_TIMEOUT", "1"));
        defaults.put("listener.ssl.netty.shutdown_timeout", envOrDefault("SSL_NETTY_SHUTDOWN_TIMEOUT", "1"));
        defaults.put("listener.ws.netty.shutdown_timeout", envOrDefault("WS_NETTY_SHUTDOWN_TIMEOUT", "1"));
        defaults.put("listener.wss.netty.shutdown_timeout", envOrDefault("WSS_NETTY_SHUTDOWN_TIMEOUT", "1"));
        return Map.copyOf(defaults);
    }

    private static Function<Environment, Object> envOrDefault(String variable, String liteValue) {
        return env -> {
            String configured = env.getProperty(variable);
            return configured == null || configured.isBlank() ? liteValue : configured;
        };
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
            shortenGracefulExecutorShutdown(environment);
        }
    }

    // ThingsBoardExecutors reads the process environment and the system properties, not the Spring environment
    private static void shortenGracefulExecutorShutdown(ConfigurableEnvironment environment) {
        if (System.getProperty(GRACEFUL_SHUTDOWN_PROPERTY) == null && environment.getProperty(GRACEFUL_SHUTDOWN_ENV) == null) {
            System.setProperty(GRACEFUL_SHUTDOWN_PROPERTY, LITE_GRACEFUL_SHUTDOWN_SEC);
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

        // the source is the live Environment, which lists this property source: the default toString would recurse
        @Override
        public String toString() {
            return getClass().getSimpleName() + " {name='" + getName() + "'}";
        }
    }

}
