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
package org.thingsboard.mqtt.broker.integration.service.integration.http;

import com.sun.net.httpserver.HttpServer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.integration.api.HttpClientPoolSettings;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks the connection pool bounds and disposal of the HTTP integration client against a real keep-alive server. */
public class TbHttpClientPoolTest {

    private static final long SHORT_IDLE_MS = 300;

    private HttpServer server;
    private CountingRelay relay;
    private EventLoopGroup sharedEventLoop;
    private TbHttpClient client;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ie", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        relay = new CountingRelay(server.getAddress().getPort());
    }

    @AfterEach
    public void tearDown() {
        if (client != null) {
            client.destroy();
        }
        if (sharedEventLoop != null) {
            sharedEventLoop.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
        relay.close();
        server.stop(0);
    }

    @Test
    public void givenNoPoolBounds_whenConnectionIdles_thenItIsReused() throws Exception {
        client = client(new HttpClientPoolSettings(0, 0, 0), null);
        call(client);
        Thread.sleep(SHORT_IDLE_MS * 3);
        call(client);
        assertThat(relay.accepted.get()).isEqualTo(1);
    }

    @Test
    public void givenIdleTimeBound_whenConnectionIdlesLonger_thenItIsClosedAndReplaced() throws Exception {
        client = client(new HttpClientPoolSettings(0, SHORT_IDLE_MS, 0), null);
        call(client);
        Thread.sleep(SHORT_IDLE_MS * 3);
        call(client);
        assertThat(relay.accepted.get()).isEqualTo(2);
        assertThat(relay.closedByClient.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void givenSharedEventLoop_whenDestroy_thenPooledConnectionIsClosed() throws Exception {
        sharedEventLoop = new NioEventLoopGroup(1);
        client = client(new HttpClientPoolSettings(0, 0, 0), sharedEventLoop);
        call(client);
        Thread.sleep(200); // the connection goes back to the pool just after the response callback
        client.destroy();
        client = null;
        assertThat(relay.closedByClient.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
    }

    private TbHttpClient client(HttpClientPoolSettings pool, EventLoopGroup eventLoop) {
        var config = new HttpIntegrationConfig("http://127.0.0.1:" + relay.port() + "/ie");
        config.setReadTimeoutMs(2000);
        IntegrationContext ctx = mock(IntegrationContext.class);
        when(ctx.getLifecycleMsg()).thenReturn(IntegrationLifecycleMsg.builder()
                .integrationId(UUID.randomUUID()).name("pool-test").build());
        when(ctx.getHttpClientPoolSettings()).thenReturn(pool);
        when(ctx.getSharedEventLoop()).thenReturn(eventLoop);
        return new TbHttpClient(config, ctx, null);
    }

    private static void call(TbHttpClient client) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        client.processMessage("{}", new BasicCallback() {
            @Override
            public void onSuccess() {
                done.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                failure.set(t);
                done.countDown();
            }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
    }

    /** TCP relay in front of the HTTP server that counts the connections the client opens and closes. */
    private static final class CountingRelay implements AutoCloseable {

        final AtomicInteger accepted = new AtomicInteger();
        final Semaphore closedByClient = new Semaphore(0);
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newCachedThreadPool();

        CountingRelay(int upstreamPort) throws IOException {
            listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            executor.execute(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket downstream = listener.accept();
                        Socket upstream = new Socket(InetAddress.getLoopbackAddress(), upstreamPort);
                        accepted.incrementAndGet();
                        executor.execute(() -> {
                            if (pump(downstream, upstream)) {
                                closedByClient.release();
                            }
                        });
                        executor.execute(() -> pump(upstream, downstream));
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
        }

        int port() {
            return listener.getLocalPort();
        }

        /** Copies bytes until the source closes its side; true on end of stream, false if the copy failed. */
        private static boolean pump(Socket from, Socket to) {
            byte[] buffer = new byte[8192];
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
                return true;
            } catch (IOException e) {
                return false;
            } finally {
                closeQuietly(from);
                closeQuietly(to);
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
            executor.shutdownNow();
        }
    }

}
