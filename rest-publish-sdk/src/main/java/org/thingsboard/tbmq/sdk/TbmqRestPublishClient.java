/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class TbmqRestPublishClient {
    private static final String PUBLISH_PATH = "/api/mqtt/publish";
    private static final String LOGIN_PATH = "/api/auth/login";

    private final URI publishUri;
    private final URI loginUri;
    private final ObjectMapper mapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final String username;
    private final String password;
    private volatile String accessToken;

    private TbmqRestPublishClient(Builder builder) {
        URI baseUri = normalizeBaseUri(builder.baseUri);
        publishUri = baseUri.resolve(PUBLISH_PATH);
        loginUri = baseUri.resolve(LOGIN_PATH);
        mapper = builder.mapper != null ? builder.mapper : new ObjectMapper();
        connectTimeoutMs = toTimeoutMillis(builder.connectTimeout, "connectTimeout");
        requestTimeoutMs = toTimeoutMillis(builder.requestTimeout, "requestTimeout");
        username = builder.username;
        password = builder.password;
        accessToken = builder.accessToken;
    }

    public static Builder builder(String baseUrl) { return new Builder(URI.create(baseUrl)); }
    public static Builder builder(URI baseUri) { return new Builder(baseUri); }

    public RestPublishResult publish(RestPublishRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            byte[] body = mapper.writeValueAsBytes(request);
            Response response = post(publishUri, body, token());
            if (response.statusCode == 401 && username != null) {
                invalidateToken();
                response = post(publishUri, body, token());
            }
            if (response.statusCode != 200 && response.statusCode != 202) {
                throw responseException("TBMQ rejected REST publish", response);
            }
            JsonNode json = mapper.readTree(response.body);
            return new RestPublishResult(response.statusCode, json.path("reasonCode").asInt(),
                    json.path("message").asText());
        } catch (TbmqRestPublishException e) {
            throw e;
        } catch (Exception e) {
            throw new TbmqRestPublishException("REST publish failed", e);
        }
    }

    public CompletableFuture<RestPublishResult> publishAsync(final RestPublishRequest request) {
        return CompletableFuture.supplyAsync(() -> publish(request));
    }

    public ObjectMapper objectMapper() { return mapper; }

    private String token() throws Exception {
        String current = accessToken;
        if (current != null) return current;
        synchronized (this) {
            if (accessToken == null) accessToken = login();
            return accessToken;
        }
    }

    private void invalidateToken() {
        synchronized (this) { accessToken = null; }
    }

    private String login() throws Exception {
        if (username == null) throw new IllegalStateException("No access token or credentials configured");
        ObjectNode login = mapper.createObjectNode().put("username", username).put("password", password);
        Response response = post(loginUri, mapper.writeValueAsBytes(login), null);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw responseException("TBMQ login failed", response);
        }
        String token = mapper.readTree(response.body).path("token").asText(null);
        if (token == null || token.trim().isEmpty()) {
            throw new TbmqRestPublishException("TBMQ login returned no token", response.statusCode, response.body);
        }
        return token;
    }

    private Response post(URI uri, byte[] body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(requestTimeoutMs);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) connection.setRequestProperty("X-Authorization", "Bearer " + token);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            try { output.write(body); } finally { output.close(); }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, input == null ? "" : readUtf8(input));
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private TbmqRestPublishException responseException(String message, Response response) {
        String detail = response.body;
        try {
            String parsed = mapper.readTree(detail).path("message").asText();
            if (!parsed.trim().isEmpty()) detail = parsed;
        } catch (Exception ignored) { }
        return new TbmqRestPublishException(message + " (HTTP " + response.statusCode + "): " + detail,
                response.statusCode, response.body);
    }

    private static URI normalizeBaseUri(URI value) {
        Objects.requireNonNull(value, "baseUri");
        if (!value.isAbsolute() || value.getHost() == null) throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URL");
        if (!"http".equalsIgnoreCase(value.getScheme()) && !"https".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalArgumentException("baseUri must use HTTP or HTTPS");
        }
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + '/');
    }

    private static int toTimeoutMillis(Duration value, String name) {
        positive(value, name);
        long millis = value.toMillis();
        if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " is too large");
        return (int) millis;
    }

    public static final class Builder {
        private final URI baseUri;
        private ObjectMapper mapper;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private String accessToken;
        private String username;
        private String password;

        private Builder(URI baseUri) { this.baseUri = baseUri; }
        public Builder accessToken(String value) {
            accessToken = requireNonBlank(value, "accessToken"); username = null; password = null; return this;
        }
        public Builder credentials(String user, String secret) {
            username = requireNonBlank(user, "username"); password = Objects.requireNonNull(secret, "password"); accessToken = null; return this;
        }
        public Builder connectTimeout(Duration value) { connectTimeout = positive(value, "connectTimeout"); return this; }
        public Builder requestTimeout(Duration value) { requestTimeout = positive(value, "requestTimeout"); return this; }
        public Builder objectMapper(ObjectMapper value) { mapper = Objects.requireNonNull(value); return this; }
        public TbmqRestPublishClient build() {
            if (accessToken == null && username == null) throw new IllegalStateException("Configure accessToken or credentials");
            return new TbmqRestPublishClient(this);
        }
        private static String requireNonBlank(String value, String name) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static final class Response {
        private final int statusCode;
        private final String body;
        private Response(int statusCode, String body) { this.statusCode = statusCode; this.body = body; }
    }
}
