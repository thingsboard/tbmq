/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TbmqClientTest {
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
    void supportsQueryHeadersAndGenericResponses() {
        server.createContext("/api/items", exchange -> {
            assertEquals("name=a+b%2Fc&page=2", exchange.getRequestURI().getRawQuery());
            assertEquals("trace-1", exchange.getRequestHeaders().getFirst("X-Trace"));
            respond(exchange, 200, "[{\"id\":1},{\"id\":2}]");
        });
        TbmqClient client = TbmqClient.builder(baseUrl).accessToken("token").build();
        TbmqApiRequest request = TbmqApiRequest.builder(TbmqHttpMethod.GET, "/api/items")
                .query("name", "a b/c").query("page", 2).header("X-Trace", "trace-1").build();
        TbmqApiResponse<List<Map<String, Integer>>> response = client.execute(request,
                new TypeReference<List<Map<String, Integer>>>() { });
        assertEquals(2, response.getBody().size());
        assertEquals(Integer.valueOf(1), response.getBody().get(0).get("id"));
    }

    @Test
    void supportsPostPutDeleteAndEmptyResponses() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/resource", exchange -> {
            int call = calls.incrementAndGet();
            assertEquals(call == 1 ? "POST" : call == 2 ? "PUT" : "DELETE", exchange.getRequestMethod());
            respond(exchange, call < 3 ? 200 : 204, call < 3 ? "{\"ok\":true}" : "");
        });
        TbmqClient client = TbmqClient.builder(baseUrl).accessToken("token").build();
        Map<String, String> body = Collections.singletonMap("name", "value");
        assertEquals(true, client.post("/api/resource", body, Map.class).get("ok"));
        assertEquals(true, client.put("/api/resource", body, Map.class).get("ok"));
        client.delete("/api/resource");
        assertEquals(3, calls.get());
    }

    @Test
    void preservesBinaryResponsesAndExposesErrors() {
        byte[] binary = new byte[]{0, -1, 10, 42};
        server.createContext("/api/binary", exchange -> {
            exchange.sendResponseHeaders(200, binary.length);
            exchange.getResponseBody().write(binary);
            exchange.close();
        });
        server.createContext("/api/error", exchange -> respond(exchange, 409, "{\"message\":\"conflict\"}"));
        TbmqClient client = TbmqClient.builder(baseUrl).accessToken("token").build();
        assertArrayEquals(binary, client.get("/api/binary", byte[].class));
        TbmqApiException error = assertThrows(TbmqApiException.class,
                () -> client.get("/api/error", String.class));
        assertEquals(409, error.getStatusCode());
        assertEquals("{\"message\":\"conflict\"}", error.getResponseBody());
    }

    @Test
    void exposesStronglyTypedDomainClients() {
        server.createContext("/api/mqtt/client/credentials", exchange -> {
            assertEquals("pageSize=10&page=0&textSearch=device", exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"data\":[{\"id\":\"c1\",\"name\":\"device-a\",\"clientType\":\"DEVICE\"}],"
                    + "\"totalPages\":1,\"totalElements\":1,\"hasNext\":false}");
        });
        server.createContext("/api/client-traces", exchange -> respond(exchange, 200,
                "[{\"id\":\"t1\",\"clientId\":\"device-a\",\"expiresAt\":\"2030-01-01T00:00:00Z\",\"level\":\"FULL\"}]"));
        TbmqClient client = TbmqClient.builder(baseUrl).accessToken("token").build();
        assertEquals("device-a", client.credentials().list(10, 0, "device").getData().get(0).getName());
        assertEquals("device-a", client.clientTraces().list().get(0).getClientId());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
