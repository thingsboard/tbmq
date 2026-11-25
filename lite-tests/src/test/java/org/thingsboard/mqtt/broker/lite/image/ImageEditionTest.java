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

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.After;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

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

    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final List<String> volumes = new ArrayList<>();

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

    @Test
    public void clusterNodeWithoutKafkaFails() throws Exception {
        String image = ImageTestSupport.requiredImage(ImageTestSupport.NODE_IMAGE_PROPERTY);
        @SuppressWarnings("resource")
        GenericContainer<?> node = new GenericContainer<>(image).withExposedPorts(8083);
        containers.add(node);
        node.start();

        // either the container exits with an error, or it is not healthy for the whole observation time
        long deadline = System.nanoTime() + NODE_OBSERVATION.toNanos();
        boolean exited = false;
        long exitCode = 0;
        boolean healthy = false;
        while (System.nanoTime() < deadline && !exited && !healthy) {
            InspectContainerResponse.ContainerState state = DockerClientFactory.lazyClient()
                    .inspectContainerCmd(node.getContainerId()).exec().getState();
            if (!Boolean.TRUE.equals(state.getRunning())) {
                exited = true;
                exitCode = state.getExitCodeLong() == null ? 0 : state.getExitCodeLong();
            } else {
                healthy = isHealthy(node);
                Thread.sleep(POLL_MILLIS);
            }
        }

        assertThat(healthy).as("a cluster node without Kafka must not become healthy").isFalse();
        if (exited) {
            assertThat(exitCode).as("exit code of the cluster node without Kafka").isNotZero();
        }
        assertThat(node.getLogs()).doesNotContain("TBMQ edition: lite");
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
