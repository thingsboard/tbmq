/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TbmqRestPublishClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() { if (server != null) server.stop(0); }

    @Test
    void publishesTextWithTokenAndMqttProperties() {
        server.createContext("/api/mqtt/publish", exchange -> {
            assertEquals("Bearer token", exchange.getRequestHeaders().getFirst("X-Authorization"));
            assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            assertEquals("devices/a/commands", body.path("topic").asText());
            assertEquals("reboot", body.path("payload").asText());
            assertEquals("TEXT", body.path("payloadEncoding").asText());
            assertEquals(1, body.path("qos").asInt());
            assertEquals("backend", body.path("properties").path("userProperties").path("source").asText());
            respond(exchange, 200, "{\"reasonCode\":0,\"message\":\"Success\"}");
        });
        TbmqRestPublishClient client = TbmqRestPublishClient.builder(baseUrl).accessToken("token").build();
        RestPublishResult result = client.publish(RestPublishRequest.text("devices/a/commands", "reboot")
                .qos(1).properties(new RestPublishProperties().userProperty("source", "backend")).build());
        assertTrue(result.isAccepted());
        assertTrue(result.hasMatchingSubscribers());
    }

    @Test
    void logsInAndRelogsOnceAfterUnauthorized() {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger publishes = new AtomicInteger();
        server.createContext("/api/auth/login", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            assertEquals("admin", body.path("username").asText());
            int login = logins.incrementAndGet();
            respond(exchange, 200, "{\"token\":\"token-" + login + "\",\"refreshToken\":\"ignored\"}");
        });
        server.createContext("/api/mqtt/publish", exchange -> {
            int call = publishes.incrementAndGet();
            if (call == 1) respond(exchange, 401, "{\"message\":\"expired\"}");
            else {
                assertEquals("Bearer token-2", exchange.getRequestHeaders().getFirst("X-Authorization"));
                respond(exchange, 202, "{\"reasonCode\":16,\"message\":\"No matching subscribers\"}");
            }
        });
        TbmqRestPublishClient client = TbmqRestPublishClient.builder(baseUrl).credentials("admin", "secret").build();
        RestPublishResult result = client.publish(RestPublishRequest.bytes("binary", new byte[]{0, 1, 2}).build());
        assertEquals(2, logins.get());
        assertEquals(2, publishes.get());
        assertFalse(result.hasMatchingSubscribers());
        assertEquals(202, result.getHttpStatus());
    }

    @Test
    void exposesErrorWithoutRetryingAmbiguousServiceUnavailable() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/mqtt/publish", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "{\"message\":\"Publish timed out waiting for the broker queue\"}");
        });
        TbmqRestPublishClient client = TbmqRestPublishClient.builder(baseUrl).accessToken("token").build();
        TbmqRestPublishException error = assertThrows(TbmqRestPublishException.class,
                () -> client.publish(RestPublishRequest.text("topic", "value").build()));
        assertEquals(503, error.getStatusCode());
        assertEquals(1, calls.get());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
