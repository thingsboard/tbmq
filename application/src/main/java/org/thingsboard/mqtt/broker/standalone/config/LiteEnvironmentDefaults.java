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

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Applies the settings of the lite edition through two property sources. The cluster edition, and a classpath
 * without an edition marker, are left untouched.
 * <ul>
 * <li>{@value #PROPERTY_SOURCE_NAME}, first in the list: {@code service.id}, which the user chooses through
 * {@code standalone.service-id}. A value is either a constant or a {@code Function<Environment, Object>} evaluated on
 * every lookup, so that key applies whether it is loaded before or after this source.</li>
 * <li>{@value #SHUTDOWN_WAITS_SOURCE_NAME}: the shortened shutdown waits. It sits just before the source of
 * {@code thingsboard-mqtt-broker.yml}, so that command-line arguments, system properties, environment variables and
 * files of {@code spring.config.additional-location} still win, while the 15000 / 1m / 5 of the file do not. Each
 * value is a placeholder on the key's environment variable, because this source hides the file's own placeholder.</li>
 * </ul>
 */
public class LiteEnvironmentDefaults implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "tbmqLiteDefaults";
    static final String SHUTDOWN_WAITS_SOURCE_NAME = "tbmqLiteShutdownWaits";

    // the name of the config-data source of the file starts with "Config resource" and holds the resource description
    static final String BROKER_YML_SOURCE_MARKER = "class path resource [thingsboard-mqtt-broker.yml]";

    static final String DEFAULT_SERVICE_ID = "tbmq-lite";

    static final String GRACEFUL_SHUTDOWN_PROPERTY = "tbmq.graceful.shutdown.timeout.sec";
    static final String GRACEFUL_SHUTDOWN_ENV = "TBMQ_GRACEFUL_SHUTDOWN_TIMEOUT_SEC";
    static final String LITE_GRACEFUL_SHUTDOWN_SEC = "1";

    private final Log log;

    /**
     * @param logFactory Spring Boot's deferred logs: logging is not set up yet when the environment is prepared
     */
    public LiteEnvironmentDefaults(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(LiteEnvironmentDefaults.class);
    }

    // logs straight away; Spring Boot uses the public constructor
    LiteEnvironmentDefaults() {
        this(Supplier::get);
    }

    static Map<String, Object> defaults() {
        Function<Environment, Object> serviceId = env -> {
            // a blank value (an empty STANDALONE_SERVICE_ID) must not fall through to the hostname
            String configured = env.getProperty("standalone.service-id");
            return configured == null || configured.isBlank() ? DEFAULT_SERVICE_ID : configured;
        };
        return Map.of("service.id", serviceId);
    }

    static Map<String, Object> shutdownWaits() {
        Map<String, Object> waits = new HashMap<>();
        waits.put("actors.system.disconnect-wait-timeout-ms", envOrDefault("ACTORS_SYSTEM_DISCONNECT_WAIT_TIMEOUT_MS", "1500"));
        waits.put("spring.lifecycle.timeout-per-shutdown-phase", envOrDefault("SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE", "6s"));
        waits.put("listener.tcp.netty.shutdown_timeout", envOrDefault("TCP_NETTY_SHUTDOWN_TIMEOUT", "1"));
        waits.put("listener.ssl.netty.shutdown_timeout", envOrDefault("SSL_NETTY_SHUTDOWN_TIMEOUT", "1"));
        waits.put("listener.ws.netty.shutdown_timeout", envOrDefault("WS_NETTY_SHUTDOWN_TIMEOUT", "1"));
        waits.put("listener.wss.netty.shutdown_timeout", envOrDefault("WSS_NETTY_SHUTDOWN_TIMEOUT", "1"));
        return Map.copyOf(waits);
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
            MutablePropertySources sources = environment.getPropertySources();
            sources.addFirst(new LazyDefaultsPropertySource(PROPERTY_SOURCE_NAME, environment, defaults()));
            addShutdownWaits(sources, new LazyDefaultsPropertySource(SHUTDOWN_WAITS_SOURCE_NAME, environment, shutdownWaits()));
            shortenGracefulExecutorShutdown(environment);
        }
    }

    /**
     * @return after the config-data post-processor, whose property sources the waits are placed against
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void addShutdownWaits(MutablePropertySources sources, PropertySource<?> waits) {
        for (PropertySource<?> source : sources) {
            if (source.getName().startsWith("Config resource") && source.getName().contains(BROKER_YML_SOURCE_MARKER)) {
                sources.addBefore(source.getName(), waits);
                return;
            }
        }
        // no config-data source for the file, for example in a unit test: below everything else
        sources.addLast(waits);
        log.info("The Lite shutdown waits are not applied: no config source for the class path thingsboard-mqtt-broker.yml "
                + "was found, so they rank below every other property source, and the file's cluster values win if it is loaded another way");
    }

    // ThingsBoardExecutors reads the process environment and the system properties, not the Spring environment
    private static void shortenGracefulExecutorShutdown(ConfigurableEnvironment environment) {
        if (System.getProperty(GRACEFUL_SHUTDOWN_PROPERTY) == null && environment.getProperty(GRACEFUL_SHUTDOWN_ENV) == null) {
            System.setProperty(GRACEFUL_SHUTDOWN_PROPERTY, LITE_GRACEFUL_SHUTDOWN_SEC);
        }
    }

    /**
     * The source is the map of values, never the environment: Spring Boot walks into a property source whose source is
     * an environment as into a nested one, and this environment lists this property source.
     */
    private static final class LazyDefaultsPropertySource extends EnumerablePropertySource<Map<String, Object>> {

        private final Environment environment;

        private LazyDefaultsPropertySource(String name, Environment environment, Map<String, Object> values) {
            super(name, values);
            this.environment = environment;
        }

        @Override
        public String[] getPropertyNames() {
            return source.keySet().toArray(new String[0]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object getProperty(String name) {
            Object value = source.get(name);
            return value instanceof Function<?, ?> function ? ((Function<Environment, Object>) function).apply(environment) : value;
        }
    }

}
