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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single-node image as a user runs it: 2 GiB, 2 CPUs, a named volume. It installs, forces the password change,
 * serves MQTT over TCP and WebSocket, and keeps a persistent session and a retained message across a restart on the
 * same volume. Run with {@code -Dtbmq.lite.image=<image>} in the {@code lite-image-tests} profile.
 */
public class LiteImageSmokeTest {

    private static final String DEFAULT_PASSWORD = "sysadmin";
    private static final String NEW_PASSWORD = "Image-test-pass-1";
    private static final String ADMIN_PASSWORD_VARIABLE_VALUE = "Image-test-admin-2";
    private static final String MQTT_USER = "image-user";
    private static final String MQTT_PASSWORD = "image-mqtt-pass";
    private static final int WAIT_SECONDS = 20;

    private static String image;

    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final List<String> volumes = new ArrayList<>();
    private final List<MqttClient> clients = new ArrayList<>();

    @BeforeClass
    public static void requireImage() {
        image = ImageTestSupport.requiredImage(ImageTestSupport.LITE_IMAGE_PROPERTY);
    }

    @After
    public void cleanUp() {
        clients.forEach(client -> {
            try {
                if (client.isConnected()) {
                    client.disconnectForcibly(0, 500);
                }
                client.close(true);
            } catch (MqttException ignored) {
                // the client is already gone
            }
        });
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
    public void installLoginPubSubRestartAndSurvive() throws Exception {
        String volume = newVolume();
        GenericContainer<?> lite = start(volume, Map.of());

        // the start script and the broker report what they chose and which edition runs
        String log = lite.getLogs();
        // the start script read the container's real cgroup files: the 2 GiB and 2 CPUs set on the container
        assertThat(log).containsPattern("TBMQ Lite: memory 2048 MiB, CPUs 2 \\(cgroup v[12]\\), collector G1, "
                + "max direct memory 204 MiB, jemalloc on").contains("TBMQ edition: lite");
        assertNoNativeLibraryInTmp(lite);

        // the image's own HEALTHCHECK, not only the readiness probe: the script passes and Docker reports healthy
        var healthcheck = lite.execInContainer("/usr/share/tbmq-lite/healthcheck.sh");
        assertThat(healthcheck.getExitCode()).as("healthcheck.sh exit code, stdout: %s, stderr: %s",
                healthcheck.getStdout(), healthcheck.getStderr()).isZero();
        ImageTestSupport.awaitDockerHealthy(lite, Duration.ofSeconds(90));

        // the runtime keeps the remote JMX agent, so the usual -Dcom.sun.management.jmxremote options in JAVA_OPTS work
        var modules = lite.execInContainer("java", "--list-modules");
        assertThat(modules.getStdout()).contains("jdk.management.agent@").contains("jdk.jcmd@").contains("jdk.attach@");

        // health: open, with component details (the image must not fall back to show-details "never")
        String base = ImageTestSupport.baseUrl(lite);
        // the UI is bundled: the SPA's index page is served at the root
        ImageTestSupport.Response ui = ImageTestSupport.get(base + "/", null);
        assertThat(ui.status()).as("GET / (the UI)").isEqualTo(200);
        assertThat(ui.body()).as("the UI index page at /").contains("<tb-root");

        ImageTestSupport.Response health = ImageTestSupport.get(base + "/actuator/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.json().path("status").asText()).isEqualTo("UP");
        assertThat(health.json().path("components").path("standaloneStore").path("details").isMissingNode())
                .as("components.standaloneStore.details in %s", health.body()).isFalse();
        // no metrics token is configured: prometheus is refused, health stays open
        assertThat(ImageTestSupport.get(base + "/actuator/prometheus", null).status()).isEqualTo(401);

        // a fresh store forces the password change: the first login is refused with a reset token
        ObjectNode login = ImageTestSupport.object().put("username", ImageTestSupport.SYSADMIN).put("password", DEFAULT_PASSWORD);
        ImageTestSupport.Response refused = ImageTestSupport.postJson(base + "/api/auth/login", null, login);
        assertThat(refused.status()).isEqualTo(401);
        assertThat(refused.json().path("errorCode").asInt()).isEqualTo(15);
        String resetToken = refused.json().path("resetToken").asText();
        assertThat(resetToken).isNotBlank();
        ObjectNode reset = ImageTestSupport.object().put("resetToken", resetToken).put("password", NEW_PASSWORD);
        ImageTestSupport.Response tokens = ImageTestSupport.postJson(base + "/api/noauth/resetPassword", null, reset);
        assertThat(tokens.status()).isEqualTo(200);
        String jwt = tokens.json().path("token").asText();
        assertThat(jwt).isNotBlank();
        assertThat(ImageTestSupport.postJson(base + "/api/auth/login", null, login).status()).isEqualTo(401);

        // a Basic DEVICE credential (the Basic provider is enabled by the seed)
        createBasicDeviceCredentials(base, jwt);

        // QoS 1 publish and subscribe over TCP 1883 and over WebSocket 8084 (path /mqtt)
        assertPubSub("tcp://" + lite.getHost() + ":" + lite.getMappedPort(1883), "image/tcp");
        assertPubSub("ws://" + lite.getHost() + ":" + lite.getMappedPort(8084) + "/mqtt", "image/ws");

        // a persistent subscriber goes away, a QoS 1 message and a retained message are published, the container restarts
        String tcp = "tcp://" + lite.getHost() + ":" + lite.getMappedPort(1883);
        MqttClient persistent = connect(tcp, "image-persistent", false, null);
        persistent.subscribe("image/queued", 1);
        persistent.disconnect();
        persistent.close();
        clients.remove(persistent);

        MqttClient publisher = connect(tcp, "image-publisher", true, null);
        publisher.publish("image/queued", "queued-payload".getBytes(StandardCharsets.UTF_8), 1, false);
        publisher.publish("image/retained", "retained-payload".getBytes(StandardCharsets.UTF_8), 1, true);
        publisher.disconnect();
        publisher.close();
        clients.remove(publisher);

        ImageTestSupport.stopGracefully(lite);
        containers.remove(lite);
        GenericContainer<?> restarted = start(volume, Map.of());
        String restartedTcp = "tcp://" + restarted.getHost() + ":" + restarted.getMappedPort(1883);

        BlockingQueue<String> queued = new LinkedBlockingQueue<>();
        MqttClient back = connect(restartedTcp, "image-persistent", false, (topic, message) ->
                queued.add(topic + "=" + new String(message.getPayload(), StandardCharsets.UTF_8)));
        assertThat(queued.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("image/queued=queued-payload");
        assertThat(back.isConnected()).isTrue();

        BlockingQueue<MqttMessage> retained = new LinkedBlockingQueue<>();
        MqttClient fresh = connect(restartedTcp, "image-fresh", true, null);
        fresh.subscribe("image/retained", 1, (topic, message) -> retained.add(message));
        MqttMessage delivered = retained.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(delivered).as("the retained message after the restart").isNotNull();
        assertThat(new String(delivered.getPayload(), StandardCharsets.UTF_8)).isEqualTo("retained-payload");
        assertThat(delivered.isRetained()).isTrue();

        // the credential and the changed password are on the volume too
        ObjectNode newLogin = ImageTestSupport.object().put("username", ImageTestSupport.SYSADMIN).put("password", NEW_PASSWORD);
        assertThat(ImageTestSupport.postJson(ImageTestSupport.baseUrl(restarted) + "/api/auth/login", null, newLogin).status())
                .isEqualTo(200);
        assertNoNativeLibraryInTmp(restarted);
    }

    @Test
    public void adminPasswordVariableLogsInDirectly() throws Exception {
        GenericContainer<?> lite = start(newVolume(), Map.of("TBMQ_ADMIN_PASSWORD", ADMIN_PASSWORD_VARIABLE_VALUE));
        String base = ImageTestSupport.baseUrl(lite);

        ObjectNode login = ImageTestSupport.object().put("username", ImageTestSupport.SYSADMIN)
                .put("password", ADMIN_PASSWORD_VARIABLE_VALUE);
        ImageTestSupport.Response response = ImageTestSupport.postJson(base + "/api/auth/login", null, login);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("token").asText()).isNotBlank();

        ObjectNode defaultLogin = ImageTestSupport.object().put("username", ImageTestSupport.SYSADMIN).put("password", DEFAULT_PASSWORD);
        assertThat(ImageTestSupport.postJson(base + "/api/auth/login", null, defaultLogin).status()).isEqualTo(401);

        // the password is never logged (the assertion message names neither the log nor the value)
        boolean leaked = lite.getLogs().contains(ADMIN_PASSWORD_VARIABLE_VALUE);
        assertThat(leaked).as("the container log contains the TBMQ_ADMIN_PASSWORD value").isFalse();
    }

    private String newVolume() {
        String volume = ImageTestSupport.newVolumeName();
        volumes.add(volume);
        return volume;
    }

    private GenericContainer<?> start(String volume, Map<String, String> env) {
        GenericContainer<?> container = ImageTestSupport.liteContainer(image, volume, env);
        containers.add(container);
        container.start();
        return container;
    }

    private static void assertNoNativeLibraryInTmp(GenericContainer<?> container) throws Exception {
        var result = container.execInContainer("sh", "-c", "find /tmp -name '*.so*' 2>/dev/null");
        assertThat(result.getStdout().trim()).as("native libraries extracted to /tmp").isEmpty();
    }

    private static void createBasicDeviceCredentials(String base, String jwt) throws IOException, InterruptedException {
        ObjectNode rules = ImageTestSupport.object();
        rules.putArray("pubAuthRulePatterns").add(".*");
        rules.putArray("subAuthRulePatterns").add(".*");
        ObjectNode basic = ImageTestSupport.object().put("userName", MQTT_USER).put("password", MQTT_PASSWORD);
        basic.set("authRules", rules);
        ObjectNode credentials = ImageTestSupport.object().put("name", "image-test-credentials")
                .put("clientType", "DEVICE").put("credentialsType", "MQTT_BASIC").put("credentialsValue", basic.toString());
        ImageTestSupport.Response saved = ImageTestSupport.postJson(base + "/api/mqtt/client/credentials", jwt, credentials);
        assertThat(saved.status()).as("create the Basic credential").isEqualTo(200);
    }

    private void assertPubSub(String uri, String topic) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        MqttClient subscriber = connect(uri, "image-sub-" + topic.replace('/', '-'), true, null);
        subscriber.subscribe(topic, 1, (t, message) -> received.add(new String(message.getPayload(), StandardCharsets.UTF_8)));
        MqttClient publisher = connect(uri, "image-pub-" + topic.replace('/', '-'), true, null);
        publisher.publish(topic, "hello".getBytes(StandardCharsets.UTF_8), 1, false);
        assertThat(received.poll(WAIT_SECONDS, TimeUnit.SECONDS)).as("QoS 1 message over %s", uri).isEqualTo("hello");
        publisher.disconnect();
        subscriber.disconnect();
    }

    /** Connects with the Basic credential; a message listener, when given, is set before the connect so queued messages are not lost. */
    private MqttClient connect(String uri, String clientId, boolean cleanSession, IMqttMessageListener queuedListener) throws MqttException {
        MqttClient client = new MqttClient(uri, clientId, new MemoryPersistence());
        clients.add(client);
        if (queuedListener != null) {
            client.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    queuedListener.messageArrived(topic, message);
                }

                @Override
                public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                }
            });
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(MQTT_USER);
        options.setPassword(MQTT_PASSWORD.toCharArray());
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(20);
        client.connect(options);
        return client;
    }

}
