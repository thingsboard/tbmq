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
package org.thingsboard.mqtt.broker.dao;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class AbstractRedisClusterContainer {

    static final String NODES = "127.0.0.1:6371,127.0.0.1:6372,127.0.0.1:6373,127.0.0.1:6374,127.0.0.1:6375,127.0.0.1:6376";
    static final String IMAGE = "valkey/valkey:8.0";
    static final String PASSWORD = "your-strong-password";

    private static GenericContainer<?> valkey(String port) {
        return new GenericContainer<>(IMAGE)
                .withNetworkMode("host")
                .withLogConsumer(AbstractRedisClusterContainer::consumeLog)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)))
                .withCommand("valkey-server",
                        "--port " + port,
                        "--requirepass " + PASSWORD,      // Password for clients connecting to this node
                        "--masterauth " + PASSWORD,       // Password for replicas to authenticate with masters
                        "--cluster-enabled yes",
                        "--cluster-config-file nodes.conf",
                        "--cluster-node-timeout 5000",
                        "--appendonly no"
                );
    }

    @ClassRule(order = 1)
    public static GenericContainer<?> valkey1 = valkey("6371");
    @ClassRule(order = 2)
    public static GenericContainer<?> valkey2 = valkey("6372");
    @ClassRule(order = 3)
    public static GenericContainer<?> valkey3 = valkey("6373");
    @ClassRule(order = 4)
    public static GenericContainer<?> valkey4 = valkey("6374");
    @ClassRule(order = 5)
    public static GenericContainer<?> valkey5 = valkey("6375");
    @ClassRule(order = 6)
    public static GenericContainer<?> valkey6 = valkey("6376");

    @ClassRule(order = 100)
    public static ExternalResource resource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            Startables.deepStart(Stream.of(valkey1, valkey2, valkey3, valkey4, valkey5, valkey6)).join();

            Thread.sleep(TimeUnit.SECONDS.toMillis(5)); // otherwise not all containers have time to start

            String clusterCreateCommand = "valkey-cli -a " + PASSWORD + " --cluster create " + NODES.replace(",", " ") + " --cluster-replicas 1 --cluster-yes";
            log.warn("Command to init ValKey Cluster: {}", clusterCreateCommand);
            var result = valkey6.execInContainer("/bin/sh", "-c", clusterCreateCommand);
            log.warn("Init cluster result: {}", result);
            Assertions.assertThat(result.getExitCode()).isEqualTo(0);

            Thread.sleep(TimeUnit.SECONDS.toMillis(5)); // otherwise cluster not always ready

            log.warn("Connect to nodes: {}", NODES);
            System.setProperty("redis.password", PASSWORD);
            System.setProperty("redis.connection.type", "cluster");
            System.setProperty("redis.cluster.nodes", NODES);
            System.setProperty("redis.cluster.useDefaultPoolConfig", "false");
        }

        @Override
        protected void after() {
            Stream.of(valkey1, valkey2, valkey3, valkey4, valkey5, valkey6).parallel().forEach(GenericContainer::stop);
            List.of("redis.password", "redis.connection.type", "redis.cluster.nodes", "redis.cluster.useDefaultPoolConfig")
                    .forEach(System.getProperties()::remove);
        }
    };

    private static void consumeLog(Object x) {
        log.warn("{}", ((OutputFrame) x).getUtf8StringWithoutLineEnding());
    }
}
