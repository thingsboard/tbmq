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
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    static final String LITE_GRACEFUL_SHUTDOWN_SEC = "1";

    /**
     * The inherited waits the Lite edition shortens. Each is set in {@code thingsboard-mqtt-broker.yml} as
     * {@code ${variable:clusterDefault}}; a source above the file hides that placeholder, so the variable is read here.
     */
    static final List<ShutdownWait> SHUTDOWN_WAITS = List.of(
            new ShutdownWait("actors.system.disconnect-wait-timeout-ms", "ACTORS_SYSTEM_DISCONNECT_WAIT_TIMEOUT_MS", "15000", "1500"),
            new ShutdownWait("spring.lifecycle.timeout-per-shutdown-phase", "SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE", "1m", "6s"),
            new ShutdownWait("listener.tcp.netty.shutdown_timeout", "TCP_NETTY_SHUTDOWN_TIMEOUT", "5", "1"),
            new ShutdownWait("listener.ssl.netty.shutdown_timeout", "SSL_NETTY_SHUTDOWN_TIMEOUT", "5", "1"),
            new ShutdownWait("listener.ws.netty.shutdown_timeout", "WS_NETTY_SHUTDOWN_TIMEOUT", "5", "1"),
            new ShutdownWait("listener.wss.netty.shutdown_timeout", "WSS_NETTY_SHUTDOWN_TIMEOUT", "5", "1"));

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

    static Map<String, Object> shutdownWaits(List<ShutdownWait> waits) {
        Map<String, Object> values = new HashMap<>();
        for (ShutdownWait wait : waits) {
            values.put(wait.key(), envOrDefault(wait.variable(), wait.liteValue()));
        }
        return Map.copyOf(values);
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
            addShutdownWaits(sources, environment);
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

    /**
     * Places the waits just above the config-data source of {@code thingsboard-mqtt-broker.yml}. A wait is left out
     * when the file sets its key to anything but the shipped placeholder (a key the file leaves out keeps the wait): an operator who edited the file, for example
     * the copy in the {@code conf} directory that the packages put on the class path, gets the value they wrote.
     */
    private void addShutdownWaits(MutablePropertySources sources, Environment environment) {
        PropertySource<?> yml = null;
        for (PropertySource<?> source : sources) {
            if (source.getName().startsWith("Config resource") && source.getName().contains(BROKER_YML_SOURCE_MARKER)) {
                yml = source;
                break;
            }
        }
        if (yml == null) {
            // no config-data source for the file, for example in a unit test: below everything else
            sources.addLast(new LazyDefaultsPropertySource(SHUTDOWN_WAITS_SOURCE_NAME, environment, shutdownWaits(SHUTDOWN_WAITS)));
            log.info("The Lite shutdown waits are not applied: no config source for the class path thingsboard-mqtt-broker.yml "
                    + "was found, so they rank below every other property source, and the file's cluster values win if it is loaded another way");
            return;
        }
        List<ShutdownWait> shipped = new ArrayList<>();
        List<String> configured = new ArrayList<>();
        for (ShutdownWait wait : SHUTDOWN_WAITS) {
            Object value = yml.getProperty(wait.key());
            // a key the file does not set at all is not an operator's value either
            if (value == null || wait.shippedValue().equals(value.toString())) {
                shipped.add(wait);
            } else {
                configured.add(wait.key());
            }
        }
        sources.addBefore(yml.getName(), new LazyDefaultsPropertySource(SHUTDOWN_WAITS_SOURCE_NAME, environment, shutdownWaits(shipped)));
        if (!configured.isEmpty()) {
            log.info("The Lite shutdown waits are not applied to " + configured + ": thingsboard-mqtt-broker.yml sets them "
                    + "to values of its own, which apply");
        }
    }

    // ThingsBoardExecutors reads the process environment and the system properties, not the Spring environment
    private static void shortenGracefulExecutorShutdown(ConfigurableEnvironment environment) {
        if (System.getProperty(ThingsBoardExecutors.GRACEFUL_SHUTDOWN_TIMEOUT_PROPERTY) == null
                && environment.getProperty(ThingsBoardExecutors.GRACEFUL_SHUTDOWN_TIMEOUT_ENV) == null) {
            System.setProperty(ThingsBoardExecutors.GRACEFUL_SHUTDOWN_TIMEOUT_PROPERTY, LITE_GRACEFUL_SHUTDOWN_SEC);
        }
    }

    /**
     * @param clusterDefault the default in the yml's placeholder, which the cluster edition uses
     * @param liteValue      the value the Lite edition uses when the variable is not set
     */
    record ShutdownWait(String key, String variable, String clusterDefault, String liteValue) {

        /**
         * @return the value the shipped yml sets the key to
         */
        String shippedValue() {
            return "${" + variable + ":" + clusterDefault + "}";
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
