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
package org.thingsboard.mqtt.broker.service.auth.providers.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpAuthCallback;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In production, a pooled connection to the auth endpoint, idle for about a day, had been
 * dropped silently by the path; the next CONNECT's request on it was answered with a TCP reset
 * ("Connection reset") and the HTTP provider failed closed. {@link ForgetfulPath} reproduces that
 * path: it forgets every idle flow it carries and resets the next packet sent on one.
 */
public class HttpAuthPoolIdleEvictionTest {
    private static final Duration SHORT_IDLE = Duration.ofMillis(300);
    private HttpServer server;
    private ExecutorService executor;
    private ForgetfulPath path;

    @Before
    public void start() throws IOException {
        executor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/auth", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"result\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        path = new ForgetfulPath(server.getAddress().getPort(), executor);
    }

    @After
    public void stop() {
        path.close();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    public void positiveBoundsAreAppliedWithBackgroundEviction() {
        HttpAuthClient.PoolSettings pool = HttpAuthClient.PoolSettings.of(32, 30_000, 300_000);
        assertThat(pool.maxConnections()).isEqualTo(32);
        assertThat(pool.maxIdleTime()).isEqualTo(Duration.ofSeconds(30));
        assertThat(pool.maxLifeTime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(pool.evictionInterval()).isEqualTo(HttpAuthClient.PoolSettings.EVICTION_INTERVAL);
    }

    @Test
    public void nonPositiveValuesFallBackToUnboundedReactorNettyPool() {
        HttpAuthClient.PoolSettings pool = HttpAuthClient.PoolSettings.of(0, 0, -1);
        assertThat(pool.maxConnections()).isEqualTo(ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS);
        assertThat(pool.maxIdleTime()).isNull();
        assertThat(pool.maxLifeTime()).isNull();
        assertThat(pool.evictionInterval()).isNull();
    }

    @Test
    public void withoutIdleEvictionAConnectionDroppedByThePathFailsTheNextConnect() throws Exception {
        HttpAuthClient client = client(HttpAuthClient.PoolSettings.of(0, 0, 0));
        try {
            assertThat(call(client)).isNull();
            awaitPoolRelease();
            path.forgetIdleFlows();
            Throwable failure = call(client);
            assertThat(failure).isNotNull();
            assertThat(rootMessage(failure)).containsIgnoringCase("reset");
            assertThat(path.accepted()).isEqualTo(1);
        } finally {
            client.destroy();
        }
    }

    @Test
    public void idleEvictionReplacesTheConnectionBeforeThePathCanDropIt() throws Exception {
        HttpAuthClient client = client(new HttpAuthClient.PoolSettings(
                ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS, SHORT_IDLE, Duration.ofMinutes(5), Duration.ofMillis(100)));
        try {
            assertThat(call(client)).isNull();
            assertThat(path.accepted()).isEqualTo(1);
            Thread.sleep(SHORT_IDLE.multipliedBy(3).toMillis());
            path.forgetIdleFlows();
            assertThat(call(client)).isNull();
            assertThat(path.accepted()).isEqualTo(2);
            assertThat(path.resets()).isZero();
        } finally {
            client.destroy();
        }
    }

    /**
     * {@link #call} returns from the response callback, and the connection goes back to the pool just after it.
     * Without this pause, the next call can race the release and open a new connection.
     */
    private static void awaitPoolRelease() throws InterruptedException {
        Thread.sleep(200);
    }

    private HttpAuthClient client(HttpAuthClient.PoolSettings pool) {
        var config = HttpMqttAuthProviderConfiguration.defaultConfiguration();
        config.setRestEndpointUrl("http://127.0.0.1:" + path.port() + "/auth");
        config.setMaxParallelRequestsCount(1);
        config.setReadTimeoutMs(1500);
        return new HttpAuthClient(config, pool);
    }

    /** Null on success, otherwise the provider's failure. */
    private static Throwable call(HttpAuthClient client) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        client.processMessage("client", "{}", new HttpAuthCallback() {
            @Override
            public void onSuccess(ResponseEntity<JsonNode> result) {
                done.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                failure.set(error);
                done.countDown();
            }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        return failure.get();
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    /** TCP relay that, like an expired conntrack entry, resets traffic on flows it forgot. */
    private static final class ForgetfulPath implements AutoCloseable {
        private final ServerSocket listener;
        private final Set<Socket[]> flows = ConcurrentHashMap.newKeySet();
        private final Set<Socket> forgotten = ConcurrentHashMap.newKeySet();
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger resets = new AtomicInteger();

        ForgetfulPath(int upstreamPort, ExecutorService executor) throws IOException {
            listener = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
            executor.execute(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket client = listener.accept();
                        Socket upstream = new Socket("127.0.0.1", upstreamPort);
                        accepted.incrementAndGet();
                        Socket[] flow = {client, upstream};
                        flows.add(flow);
                        executor.execute(() -> pump(flow, client, upstream, true));
                        executor.execute(() -> pump(flow, upstream, client, false));
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
        }

        int port() {
            return listener.getLocalPort();
        }

        int accepted() {
            return accepted.get();
        }

        int resets() {
            return resets.get();
        }

        void forgetIdleFlows() {
            flows.forEach(flow -> forgotten.add(flow[0]));
        }

        private void pump(Socket[] flow, Socket from, Socket to, boolean fromClient) {
            byte[] buffer = new byte[8192];
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (fromClient && forgotten.contains(from)) {
                        resets.incrementAndGet();
                        from.setSoLinger(true, 0); // RST, as for a flow the path no longer knows
                        from.close();
                        to.close();
                        return;
                    }
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException closed) {
                // either side closed the flow
            } finally {
                flows.remove(flow);
                closeQuietly(to);
                closeQuietly(from);
            }
        }

        private static void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing
            }
        }

        @Override
        public void close() {
            try {
                listener.close();
            } catch (IOException ignored) {
                // closing
            }
            flows.forEach(flow -> {
                closeQuietly(flow[0]);
                closeQuietly(flow[1]);
            });
        }
    }
}
