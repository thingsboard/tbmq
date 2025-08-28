/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AbstractRedisClusterContainer {

    static final String nodes = "127.0.0.1:6371,127.0.0.1:6372,127.0.0.1:6373,127.0.0.1:6374,127.0.0.1:6375,127.0.0.1:6376";

    private static GenericContainer<?> redis(String port) {
        return new GenericContainer<>("bitnamilegacy/redis-cluster:7.2.5")
                .withEnv("REDIS_PORT_NUMBER", port)
                .withNetworkMode("host")
                .withLogConsumer(x -> log.warn("{}", x.getUtf8StringWithoutLineEnding()))
                .withEnv("REDIS_PASSWORD", "password")
                .withEnv("REDIS_NODES", nodes)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    }

    @ClassRule
    public static Network network = Network.newNetwork();
    @ClassRule
    public static GenericContainer<?> redis1 = redis("6371");
    @ClassRule
    public static GenericContainer<?> redis2 = redis("6372");
    @ClassRule
    public static GenericContainer<?> redis3 = redis("6373");
    @ClassRule
    public static GenericContainer<?> redis4 = redis("6374");
    @ClassRule
    public static GenericContainer<?> redis5 = redis("6375");
    @ClassRule
    public static GenericContainer<?> redis6 = redis("6376");

    @ClassRule
    public static ExternalResource resource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            redis1.start();
            redis2.start();
            redis3.start();
            redis4.start();
            redis5.start();
            redis6.start();

            Thread.sleep(TimeUnit.SECONDS.toMillis(5)); // otherwise not all containers have time to start

            String clusterCreateCommand = "echo yes | redis-cli --cluster create " +
                    nodes.replace(",", " ") +
                    " --cluster-replicas 1";
            log.warn("Command to init Redis Cluster: {}", clusterCreateCommand);
            var result = redis6.execInContainer("/bin/sh", "-c", "export REDISCLI_AUTH=password && " + clusterCreateCommand);
            log.warn("Init cluster result: {}", result);

            Thread.sleep(TimeUnit.SECONDS.toMillis(5)); // otherwise cluster not always ready

            log.warn("Connect to nodes: {}", nodes);
            System.setProperty("redis.password", "password");
            System.setProperty("redis.connection.type", "cluster");
            System.setProperty("redis.cluster.nodes", nodes);
            System.setProperty("redis.cluster.useDefaultPoolConfig", "false");
        }

        @Override
        protected void after() {
            redis1.stop();
            redis2.stop();
            redis3.stop();
            redis4.stop();
            redis5.stop();
            redis6.stop();
            List.of("redis.password", "redis.connection.type", "redis.cluster.nodes", "redis.cluster.useDefaultPoolConfig")
                    .forEach(System.getProperties()::remove);
        }
    };

}
