/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Java 8 client for every TBMQ REST endpoint. Business-specific clients can be
 * added without duplicating authentication and HTTP handling.
 */
public final class TbmqClient {
    private static final String LOGIN_PATH = "/api/auth/login";

    private final URI baseUri;
    private final ObjectMapper mapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final String username;
    private final String password;
    private volatile String accessToken;

    private TbmqClient(Builder builder) {
        baseUri = normalizeBaseUri(builder.baseUri);
        mapper = builder.mapper != null ? builder.mapper : new ObjectMapper();
        connectTimeoutMs = toTimeoutMillis(builder.connectTimeout, "connectTimeout");
        requestTimeoutMs = toTimeoutMillis(builder.requestTimeout, "requestTimeout");
        username = builder.username;
        password = builder.password;
        accessToken = builder.accessToken;
    }

    public static Builder builder(String baseUrl) { return new Builder(URI.create(baseUrl)); }
    public static Builder builder(URI baseUri) { return new Builder(baseUri); }

    public <T> T get(String path, Class<T> responseType) {
        return execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, path).build(), responseType).getBody();
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return execute(TbmqApiRequest.builder(TbmqHttpMethod.POST, path).body(body).build(), responseType).getBody();
    }

    public <T> T put(String path, Object body, Class<T> responseType) {
        return execute(TbmqApiRequest.builder(TbmqHttpMethod.PUT, path).body(body).build(), responseType).getBody();
    }

    public void delete(String path) {
        execute(TbmqApiRequest.builder(TbmqHttpMethod.DELETE, path).build(), Void.class);
    }

    public JsonNode execute(TbmqApiRequest request) {
        return execute(request, JsonNode.class).getBody();
    }

    public <T> TbmqApiResponse<T> execute(TbmqApiRequest request, Class<T> responseType) {
        Objects.requireNonNull(responseType, "responseType");
        return executeInternal(request, new BodyReader<T>() {
            @Override public T read(String body, byte[] bytes) throws Exception {
                if (responseType == byte[].class) return responseType.cast(bytes);
                if (responseType == Void.class || body == null || body.trim().isEmpty()) return null;
                return mapper.readValue(body, responseType);
            }
        });
    }

    public <T> TbmqApiResponse<T> execute(TbmqApiRequest request, final TypeReference<T> responseType) {
        Objects.requireNonNull(responseType, "responseType");
        return executeInternal(request, new BodyReader<T>() {
            @Override public T read(String body, byte[] bytes) throws Exception {
                if (body == null || body.trim().isEmpty()) return null;
                return mapper.readValue(body, responseType);
            }
        });
    }

    public <T> CompletableFuture<TbmqApiResponse<T>> executeAsync(final TbmqApiRequest request,
                                                                  final Class<T> responseType) {
        return CompletableFuture.supplyAsync(() -> execute(request, responseType));
    }

    public TbmqRestPublishClient mqttPublish() { return new TbmqRestPublishClient(this); }
    public CredentialsClient credentials() { return new CredentialsClient(this); }
    public AuthProvidersClient authProviders() { return new AuthProvidersClient(this); }
    public SessionsClient sessions() { return new SessionsClient(this); }
    public SubscriptionsClient subscriptions() { return new SubscriptionsClient(this); }
    public RetainedMessagesClient retainedMessages() { return new RetainedMessagesClient(this); }
    public IntegrationsClient integrations() { return new IntegrationsClient(this); }
    public ClientTracesClient clientTraces() { return new ClientTracesClient(this); }
    public ObjectMapper objectMapper() { return mapper; }

    private <T> TbmqApiResponse<T> executeInternal(TbmqApiRequest request, BodyReader<T> reader) {
        Objects.requireNonNull(request, "request");
        try {
            RawResponse response = send(request, request.isAuthenticated() ? token() : null);
            if (response.statusCode == 401 && request.isAuthenticated() && username != null) {
                invalidateToken();
                response = send(request, token());
            }
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw responseException("TBMQ REST request failed", response);
            }
            return new TbmqApiResponse<T>(response.statusCode, reader.read(response.body, response.bytes), response.headers);
        } catch (TbmqApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TbmqApiException("TBMQ REST request failed", e);
        }
    }

    private RawResponse send(TbmqApiRequest request, String token) throws Exception {
        URI uri = buildUri(request.getPath(), request.getQueryParameters());
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(requestTimeoutMs);
            connection.setRequestMethod(request.getMethod().name());
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) connection.setRequestProperty("X-Authorization", "Bearer " + token);
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (request.getBody() != null) {
                byte[] bytes = request.getBody() instanceof byte[] ? (byte[]) request.getBody()
                        : mapper.writeValueAsBytes(request.getBody());
                connection.setDoOutput(true);
                if (!request.getHeaders().containsKey("Content-Type")) {
                    connection.setRequestProperty("Content-Type", "application/json");
                }
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try { output.write(bytes); } finally { output.close(); }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] bytes = input == null ? new byte[0] : readBytes(input);
            String body = new String(bytes, StandardCharsets.UTF_8);
            Map<String, List<String>> headers = connection.getHeaderFields();
            return new RawResponse(status, body, bytes,
                    headers == null ? Collections.<String, List<String>>emptyMap() : headers);
        } finally {
            connection.disconnect();
        }
    }

    private String token() throws Exception {
        String current = accessToken;
        if (current != null) return current;
        synchronized (this) {
            if (accessToken == null) accessToken = login();
            return accessToken;
        }
    }

    private String login() throws Exception {
        if (username == null) throw new IllegalStateException("No access token or credentials configured");
        ObjectNode body = mapper.createObjectNode().put("username", username).put("password", password);
        TbmqApiRequest request = TbmqApiRequest.builder(TbmqHttpMethod.POST, LOGIN_PATH)
                .body(body).authenticated(false).build();
        RawResponse response = send(request, null);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw responseException("TBMQ login failed", response);
        }
        String token = mapper.readTree(response.body).path("token").asText(null);
        if (token == null || token.trim().isEmpty()) {
            throw new TbmqApiException("TBMQ login returned no token", response.statusCode, response.body);
        }
        return token;
    }

    private void invalidateToken() { synchronized (this) { accessToken = null; } }

    private URI buildUri(String path, Map<String, String> query) throws Exception {
        StringBuilder value = new StringBuilder(baseUri.resolve(path).toString());
        char separator = value.indexOf("?") >= 0 ? '&' : '?';
        for (Map.Entry<String, String> entry : query.entrySet()) {
            value.append(separator);
            separator = '&';
            value.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            value.append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return URI.create(value.toString());
    }

    private TbmqApiException responseException(String message, RawResponse response) {
        String detail = response.body;
        try {
            String parsed = mapper.readTree(detail).path("message").asText();
            if (!parsed.trim().isEmpty()) detail = parsed;
        } catch (Exception ignored) { }
        return new TbmqApiException(message + " (HTTP " + response.statusCode + "): " + detail,
                response.statusCode, response.body);
    }

    private static byte[] readBytes(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally { input.close(); }
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

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
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
        public TbmqClient build() {
            if (accessToken == null && username == null) throw new IllegalStateException("Configure accessToken or credentials");
            return new TbmqClient(this);
        }
        private static String requireNonBlank(String value, String name) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }

    private interface BodyReader<T> { T read(String body, byte[] bytes) throws Exception; }

    private static final class RawResponse {
        private final int statusCode;
        private final String body;
        private final byte[] bytes;
        private final Map<String, List<String>> headers;
        private RawResponse(int statusCode, String body, byte[] bytes, Map<String, List<String>> headers) {
            this.statusCode = statusCode; this.body = body; this.bytes = bytes; this.headers = headers;
        }
    }
}
