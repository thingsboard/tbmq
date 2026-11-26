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
package org.thingsboard.mqtt.broker.lite;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the image's healthcheck script against a local HTTP server standing in for the broker's actuator.
 */
public class HealthcheckScriptTest {

    private static final String UP = "{\"status\":\"UP\",\"components\":{}}";
    private static final String DOWN = "{\"status\":\"DOWN\"}";

    private final List<HttpServer> servers = new ArrayList<>();
    private Path script;

    @Before
    public void setUp() {
        String path = System.getProperty("tbmq.lite.healthcheck-script");
        assertNotNull("system property tbmq.lite.healthcheck-script (the healthcheck script's path) is not set", path);
        script = new File(path).toPath().toAbsolutePath().normalize();
        assertTrue("healthcheck script not found: " + script, Files.isRegularFile(script));
    }

    @After
    public void tearDown() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    public void upPasses() throws Exception {
        int port = serve("127.0.0.1", 200, UP);

        assertEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port))));
    }

    @Test
    public void downFails() throws Exception {
        int port = serve("127.0.0.1", 503, DOWN);

        assertNotEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port))));
    }

    @Test
    public void upInANon200AnswerFails() throws Exception {
        int port = serve("127.0.0.1", 500, UP);

        assertNotEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port))));
    }

    @Test
    public void nothingListeningFails() throws Exception {
        assertNotEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(freePort()))));
    }

    @Test
    public void aSpecificBindAddressIsUsed() throws Exception {
        // the broker listens only on 127.0.0.2 (HTTP_BIND_ADDRESS): 127.0.0.1 refuses the connection
        int port = serve("127.0.0.2", 200, UP);

        assertEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port), "HTTP_BIND_ADDRESS", "127.0.0.2")));
        assertNotEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port))));
    }

    @Test
    public void aWildcardBindAddressUsesLoopback() throws Exception {
        int port = serve("127.0.0.1", 200, UP);

        for (String wildcard : new String[]{"0.0.0.0", "::", "[::]", ""}) {
            assertEquals(wildcard, 0,
                    check(Map.of("HTTP_BIND_PORT", Integer.toString(port), "HTTP_BIND_ADDRESS", wildcard)));
        }
    }

    @Test
    public void withSslOnlyTheConnectionIsChecked() throws Exception {
        // a plain-HTTP request cannot be answered by a TLS port; accepting the connection is what can be checked
        int port = serve("127.0.0.1", 503, DOWN);

        assertEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port), "SSL_ENABLED", "true")));
        assertEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(port), "SSL_ENABLED", "TRUE")));
        assertNotEquals(0, check(Map.of("HTTP_BIND_PORT", Integer.toString(freePort()), "SSL_ENABLED", "true")));
    }

    private int serve(String address, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(address), 0), 0);
        server.createContext("/actuator/health", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        servers.add(server);
        return server.getAddress().getPort();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private int check(Map<String, String> vars) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", script.toString());
        Map<String, String> env = pb.environment();
        env.remove("HTTP_BIND_PORT");
        env.remove("HTTP_BIND_ADDRESS");
        env.remove("SSL_ENABLED");
        env.remove("TBMQ_HEALTHCHECK_BOUNDED");
        env.putAll(vars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        assertTrue("healthcheck timed out", process.waitFor(30, TimeUnit.SECONDS));
        return process.exitValue();
    }

}
