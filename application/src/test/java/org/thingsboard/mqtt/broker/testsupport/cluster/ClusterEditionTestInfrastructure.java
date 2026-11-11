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
package org.thingsboard.mqtt.broker.testsupport.cluster;

import lombok.extern.slf4j.Slf4j;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.testsupport.EditionTestInfrastructure;

import java.util.List;

/**
 * The cluster edition's test infrastructure: Kafka and Valkey in Testcontainers, started and stopped per test class.
 */
@Slf4j
public class ClusterEditionTestInfrastructure implements EditionTestInfrastructure {

    private static final int VALKEY_PORT = 6379;
    private static final String VALKEY_PASSWORD = "password";

    private static final List<String> PROPERTIES = List.of(
            "queue.kafka.bootstrap.servers",
            "redis.connection.type",
            "redis.standalone.host",
            "redis.standalone.port",
            "redis.password");

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.2"));

    private static final GenericContainer<?> VALKEY = new GenericContainer<>("valkey/valkey:8.0")
            .withExposedPorts(VALKEY_PORT)
            .withLogConsumer(x -> log.warn("{}", x.getUtf8StringWithoutLineEnding()))
            .withCommand("valkey-server", "--requirepass", VALKEY_PASSWORD);

    /**
     * @return the Valkey container of the class that is currently running, e.g. to simulate an outage
     */
    public static GenericContainer<?> valkey() {
        return VALKEY;
    }

    @Override
    public TestRule classRule() {
        return RuleChain.outerRule(KAFKA).around(VALKEY).around(new ExternalResource() {
            @Override
            protected void before() {
                System.setProperty("queue.kafka.bootstrap.servers", KAFKA.getBootstrapServers());
                System.setProperty("redis.connection.type", "standalone");
                System.setProperty("redis.standalone.host", VALKEY.getHost());
                System.setProperty("redis.standalone.port", String.valueOf(VALKEY.getMappedPort(VALKEY_PORT)));
                System.setProperty("redis.password", VALKEY_PASSWORD);
            }

            @Override
            protected void after() {
                PROPERTIES.forEach(System.getProperties()::remove);
            }
        });
    }

}
