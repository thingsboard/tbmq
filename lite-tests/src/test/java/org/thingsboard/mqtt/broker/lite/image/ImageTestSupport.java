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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What the image tests share: the image names (a missing property fails the test with a message naming it), a container
 * started with the limits the image is meant for, named volumes, and a small REST client.
 */
final class ImageTestSupport {

    static final String LITE_IMAGE_PROPERTY = "tbmq.lite.image";
    static final String NODE_IMAGE_PROPERTY = "tbmq.node.image";
    static final String SYSADMIN = "sysadmin@thingsboard.org";
    static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    private static final long MEMORY_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long NANO_CPUS = 2_000_000_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private ImageTestSupport() {
    }

    static String requiredImage(String property) {
        String image = System.getProperty(property);
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("Set -D" + property + "=<image:tag> to run the image tests; "
                    + "they run in the lite-image-tests profile against an image you built");
        }
        return image;
    }

    static String newVolumeName() {
        return "tbmq-lite-image-test-" + UUID.randomUUID();
    }

    /**
     * A Lite container with 2 GiB and 2 CPUs, the named volume on /data, and the health endpoint as the readiness check.
     * Docker maps random host ports.
     */
    @SuppressWarnings("resource")
    static GenericContainer<?> liteContainer(String image, String volume, Map<String, String> env) {
        GenericContainer<?> container = new GenericContainer<>(image)
                .withExposedPorts(1883, 8083, 8084)
                .withEnv(env)
                .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig())
                        .withMemory(MEMORY_BYTES)
                        .withMemorySwap(MEMORY_BYTES)
                        .withNanoCPUs(NANO_CPUS)
                        .withBinds(new Bind(volume, new Volume("/data"))))
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8083).forStatusCode(200)
                        .withStartupTimeout(STARTUP_TIMEOUT));
        return container;
    }

    /** Stops the container with SIGTERM and the grace period the broker's shutdown needs, then removes it. */
    static void stopGracefully(GenericContainer<?> container) {
        DockerClient docker = DockerClientFactory.lazyClient();
        try {
            docker.stopContainerCmd(container.getContainerId()).withTimeout(60).exec();
        } finally {
            container.stop();
        }
    }

    static void removeVolume(String volume) {
        try {
            DockerClientFactory.lazyClient().removeVolumeCmd(volume).exec();
        } catch (RuntimeException ignored) {
            // the volume was never created or is already gone
        }
    }

    /**
     * Waits for Docker's own health status of the container (the image's HEALTHCHECK) to become healthy; fails at once
     * on unhealthy, or when it is still starting after the timeout.
     */
    static void awaitDockerHealthy(GenericContainer<?> container, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        String status = null;
        while (System.nanoTime() < deadline) {
            InspectContainerResponse.ContainerState state = DockerClientFactory.lazyClient()
                    .inspectContainerCmd(container.getContainerId()).exec().getState();
            status = state.getHealth() == null ? null : state.getHealth().getStatus();
            if ("healthy".equals(status)) {
                return;
            }
            if ("unhealthy".equals(status)) {
                throw new AssertionError("Docker reports the container unhealthy: " + state.getHealth().getLog());
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Docker health status is still " + status + " after " + timeout);
    }

    static String baseUrl(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(8083);
    }

    static Response get(String url, String bearerToken) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        if (bearerToken != null) {
            request.header("X-Authorization", "Bearer " + bearerToken);
        }
        return send(request.build());
    }

    static Response postJson(String url, String bearerToken, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        if (bearerToken != null) {
            request.header("X-Authorization", "Bearer " + bearerToken);
        }
        return send(request.build());
    }

    private static Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    static JsonNode json(String body) throws IOException {
        return MAPPER.readTree(body);
    }

    record Response(int status, String body) {

        JsonNode json() throws IOException {
            return MAPPER.readTree(body);
        }

    }

}
