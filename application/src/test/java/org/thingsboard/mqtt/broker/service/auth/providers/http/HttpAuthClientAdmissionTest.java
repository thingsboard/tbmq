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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks admission through the real HTTP caller, including absence of rejected requests. */
public class HttpAuthClientAdmissionTest {
    private HttpServer server;
    private HttpAuthClient client;
    private final Semaphore requests = new Semaphore(0);
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.release();
            byte[] body = "{\"result\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var config = HttpMqttAuthProviderConfiguration.defaultConfiguration();
        config.setRestEndpointUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/auth");
        config.setMaxParallelRequestsCount(1);
        config.setReadTimeoutMs(2000);
        client = new HttpAuthClient(config);
        // Establish a working real HTTP connection before any negative control.
        var warmup = new Result();
        client.processMessage("warmup", "{}", warmup);
        warmup.await();
        assertThat(warmup.successes.get()).isEqualTo(1);
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    @After
    public void tearDown() {
        Thread.interrupted();
        if (client != null) client.destroy();
        if (server != null) server.stop(0);
    }

    @Test
    public void exhaustedAdmissionMustNotSendRequestOrReleaseAnUnownedPermit() throws Exception {
        // Model a permit already owned by another request without relying on two
        // racing HTTP-read and admission timers expiring at the same instant.
        client.getSemaphore().acquire();
        try {
            var rejected = new Result();
            client.processMessage("rejected", "{}", rejected);
            rejected.await();
            assertThat(rejected.failures.get()).isEqualTo(1);
            assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isFalse();
            assertThat(rejected.successes.get()).isZero();
            assertThat(client.getSemaphore().availablePermits()).isZero();
        } finally {
            client.getSemaphore().release();
        }
        var recovered = new Result();
        client.processMessage("recovered", "{}", recovered);
        recovered.await();
        assertThat(recovered.successes.get()).isEqualTo(1);
        assertThat(recovered.failures.get()).isZero();
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    @Test
    public void interruptedAdmissionPreservesTheInterruptAndDoesNotSend() throws Exception {
        client.getSemaphore().acquire();
        var rejected = new Result();
        try {
            Thread.currentThread().interrupt();
            client.processMessage("interrupted", "{}", rejected);
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
    public void interruptedConnectionProbePreservesTheInterruptAndDoesNotSend() throws Exception {
        client.getSemaphore().acquire();
        var rejected = new Result();
        try {
            Thread.currentThread().interrupt();
            client.checkConnection(rejected);
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
    public void httpFailureReleasesTheOwnedPermitForTheNextRequest() throws Exception {
        responseStatus.set(503);
        var failure = new Result();
        client.processMessage("http-failure", "{}", failure);
        failure.await();
        assertThat(failure.failures.get()).isEqualTo(1);
        assertThat(failure.successes.get()).isZero();
        assertThat(requests.tryAcquire(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
        responseStatus.set(200);
        var success = new Result();
        client.processMessage("retry", "{}", success);
        success.await();
        assertThat(success.successes.get()).isEqualTo(1);
        assertThat(success.failures.get()).isZero();
        assertThat(client.getSemaphore().availablePermits()).isEqualTo(1);
    }

    private static class Result implements HttpAuthCallback {
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(1);
        @Override public void onSuccess(ResponseEntity<JsonNode> result) { successes.incrementAndGet(); done.countDown(); }
        @Override public void onFailure(Throwable error) { failures.incrementAndGet(); done.countDown(); }
        void await() throws InterruptedException { assertThat(done.await(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
