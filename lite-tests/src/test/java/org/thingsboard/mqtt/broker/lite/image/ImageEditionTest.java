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
package org.thingsboard.mqtt.broker.lite.image;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.After;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edition each image runs: {@code tbmq-lite} ignores the cluster variables, {@code tbmq-node} without Kafka fails
 * instead of falling back to Lite. Run with {@code -Dtbmq.lite.image} and {@code -Dtbmq.node.image} in the
 * {@code lite-image-tests} profile.
 */
public class ImageEditionTest {

    private static final Duration NODE_OBSERVATION = Duration.ofSeconds(120);
    private static final long POLL_MILLIS = 2000;
    private static final String UNRESOLVABLE_KAFKA = "kafka.invalid:9092";

    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final List<String> volumes = new ArrayList<>();
    private final List<Network> networks = new ArrayList<>();

    @After
    public void cleanUp() {
        containers.forEach(container -> {
            try {
                container.stop();
            } catch (RuntimeException ignored) {
                // already removed
            }
        });
        volumes.forEach(ImageTestSupport::removeVolume);
        networks.forEach(Network::close);
    }

    @Test
    public void liteIgnoresClusterVariables() {
        String image = ImageTestSupport.requiredImage(ImageTestSupport.LITE_IMAGE_PROPERTY);
        String volume = ImageTestSupport.newVolumeName();
        volumes.add(volume);
        GenericContainer<?> lite = ImageTestSupport.liteContainer(image, volume, Map.of(
                "TB_KAFKA_SERVERS", "kafka.invalid:9092",
                "REDIS_HOST", "redis.invalid",
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres.invalid:5432/thingsboard_mqtt_broker"));
        containers.add(lite);

        lite.start(); // returns once /actuator/health answers 200

        assertThat(lite.getLogs()).contains("TBMQ edition: lite");
    }

    /**
     * The node gets a reachable PostgreSQL and Valkey and a Kafka address that does not resolve, so the first thing that
     * can stop it is Kafka: it must exit with an error naming the Kafka admin client, not fall back to Lite.
     */
    @Test
    public void clusterNodeWithoutKafkaFails() throws Exception {
        String image = ImageTestSupport.requiredImage(ImageTestSupport.NODE_IMAGE_PROPERTY);
        Network network = Network.newNetwork();
        networks.add(network);
        @SuppressWarnings("resource")
        GenericContainer<?> postgres = new GenericContainer<>("postgres:17")
                .withNetwork(network).withNetworkAliases("postgres")
                .withEnv("POSTGRES_PASSWORD", "postgres").withEnv("POSTGRES_DB", "thingsboard_mqtt_broker")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
        @SuppressWarnings("resource")
        GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:8.0")
                .withNetwork(network).withNetworkAliases("valkey")
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
        containers.add(postgres);
        containers.add(valkey);
        postgres.start();
        valkey.start();

        // no wait and no startup check: the node is expected to exit, which must not make start() throw
        @SuppressWarnings("resource")
        GenericContainer<?> node = new GenericContainer<>(image)
                .withNetwork(network)
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/thingsboard_mqtt_broker")
                .withEnv("REDIS_HOST", "valkey")
                .withEnv("TB_KAFKA_SERVERS", UNRESOLVABLE_KAFKA)
                .withExposedPorts(8083)
                .withStartupCheckStrategy(new StartupCheckStrategy() {
                    @Override
                    public StartupStatus checkStartupState(DockerClient dockerClient, String containerId) {
                        return StartupStatus.SUCCESSFUL;
                    }
                })
                .waitingFor(new AbstractWaitStrategy() {
                    @Override
                    protected void waitUntilReady() {
                    }
                });
        containers.add(node);
        node.start();

        // either the container exits with an error, or it is not healthy for the whole observation time
        long deadline = System.nanoTime() + NODE_OBSERVATION.toNanos();
        boolean exited = false;
        Long exitCode = null;
        boolean healthy = false;
        while (System.nanoTime() < deadline && !exited && !healthy) {
            InspectContainerResponse.ContainerState state = DockerClientFactory.lazyClient()
                    .inspectContainerCmd(node.getContainerId()).exec().getState();
            if (Boolean.TRUE.equals(state.getRunning())) {
                healthy = isHealthy(node);
                Thread.sleep(POLL_MILLIS);
            } else if (state.getExitCodeLong() != null) {
                exited = true;
                exitCode = state.getExitCodeLong();
            } else {
                Thread.sleep(POLL_MILLIS); // not started yet
            }
        }

        String log = node.getLogs();
        assertThat(healthy).as("a cluster node without Kafka must not become healthy").isFalse();
        assertThat(exited).as("the cluster node exits instead of waiting for Kafka").isTrue();
        assertThat(exitCode).as("exit code of the cluster node without Kafka").isNotNull().isNotZero();
        // the cluster entry point ran, with the Kafka address of this test
        assertThat(log).contains("Starting ThingsboardMqttBrokerApplication").contains("bootstrap.servers = [" + UNRESOLVABLE_KAFKA + "]");
        // and Kafka is what stopped it: not PostgreSQL, not Valkey
        assertThat(log).contains("Failed to instantiate [org.thingsboard.mqtt.broker.queue.kafka.TbKafkaAdmin]")
                .contains("KafkaException: Failed to create new KafkaAdminClient")
                .contains("No resolvable bootstrap urls given in bootstrap.servers")
                .doesNotContain("JDBCConnectionException")
                .doesNotContain("RedisConnectionFailureException");
        assertThat(log).doesNotContain("TBMQ edition: lite");
    }

    private static boolean isHealthy(GenericContainer<?> node) {
        try {
            ImageTestSupport.Response response = ImageTestSupport.get(ImageTestSupport.baseUrl(node) + "/actuator/health", null);
            return response.status() == 200 && "UP".equals(response.json().path("status").asText());
        } catch (Exception e) {
            return false;
        }
    }

}
