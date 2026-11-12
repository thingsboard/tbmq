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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.integration.api.HttpClientPoolSettings;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks request-slot admission through the real HTTP client, including absence of rejected requests. */
public class TbHttpClientAdmissionTest {

    private HttpServer server;
    private TbHttpClient client;
    private Result checkConnectionResult;
    private final Semaphore requests = new Semaphore(0);
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ie", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.release();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var config = new HttpIntegrationConfig("http://127.0.0.1:" + server.getAddress().getPort() + "/ie");
        config.setMaxParallelRequestsCount(1);
        config.setReadTimeoutMs(1000);

        checkConnectionResult = new Result();
        IntegrationContext ctx = mock(IntegrationContext.class);
        when(ctx.getLifecycleMsg()).thenReturn(IntegrationLifecycleMsg.builder()
                .integrationId(UUID.randomUUID()).name("admission-test").build());
        when(ctx.getCheckConnectionCallback()).thenReturn(checkConnectionResult);
        when(ctx.getHttpClientPoolSettings()).thenReturn(new HttpClientPoolSettings(0, 30_000, 300_000));

        client = new TbHttpClient(config, ctx, null);
        // Establish a working real HTTP connection before any negative control.
        var warmup = new Result();
        client.processMessage("{}", warmup);
        warmup.await();
        assertThat(warmup.successes.get()).isEqualTo(1);
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    @AfterEach
    public void tearDown() {
        Thread.interrupted();
        if (client != null) {
            client.destroy();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void givenNoFreeRequestSlot_whenProcessMessage_thenNoRequestIsSentAndNoUnownedPermitIsReleased() throws Exception {
        // Model a permit already owned by another in-flight request.
        client.getSemaphore().acquire();
        try {
            var rejected = new Result();
            client.processMessage("{}", rejected);
            rejected.await();
            assertThat(rejected.failures.get()).isEqualTo(1);
            assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isFalse();
            assertThat(rejected.successes.get()).isZero();
            assertThat(client.getSemaphore().availablePermits()).isZero();
        } finally {
            client.getSemaphore().release();
        }

        var recovered = new Result();
        client.processMessage("{}", recovered);
        recovered.await();
        assertThat(recovered.successes.get()).isEqualTo(1);
        assertThat(recovered.failures.get()).isZero();
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    @Test
    public void givenInterruptedWaitForSlot_whenProcessMessage_thenInterruptIsPreservedAndNoRequestIsSent() throws Exception {
        client.getSemaphore().acquire();
        var rejected = new Result();
        try {
            Thread.currentThread().interrupt();
            client.processMessage("{}", rejected);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            client.getSemaphore().release();
        }
        rejected.await();
        assertThat(rejected.failures.get()).isEqualTo(1);
        assertThat(rejected.successes.get()).isZero();
        assertThat(requests.tryAcquire(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    @Test
    public void givenInterruptedWaitForSlot_whenCheckConnection_thenInterruptIsPreservedAndNoRequestIsSent() throws Exception {
        client.getSemaphore().acquire();
        try {
            Thread.currentThread().interrupt();
            client.checkConnection();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            client.getSemaphore().release();
        }
        checkConnectionResult.await();
        assertThat(checkConnectionResult.failures.get()).isEqualTo(1);
        assertThat(checkConnectionResult.successes.get()).isZero();
        assertThat(requests.tryAcquire(300, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    @Test
    public void givenHttpFailure_whenProcessMessage_thenOwnedPermitIsReleasedForTheNextRequest() throws Exception {
        responseStatus.set(503);
        var failure = new Result();
        client.processMessage("{}", failure);
        failure.await();
        assertThat(failure.failures.get()).isEqualTo(1);
        assertThat(failure.successes.get()).isZero();
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);

        responseStatus.set(200);
        var success = new Result();
        client.processMessage("{}", success);
        success.await();
        assertThat(success.successes.get()).isEqualTo(1);
        assertThat(success.failures.get()).isZero();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    private static class Result implements BasicCallback {

        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onSuccess() {
            successes.incrementAndGet();
            done.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            failures.incrementAndGet();
            done.countDown();
        }

        void await() throws InterruptedException {
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

}
