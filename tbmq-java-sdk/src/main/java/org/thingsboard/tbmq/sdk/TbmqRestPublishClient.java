/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Strongly typed facade for {@code POST /api/mqtt/publish}. */
public final class TbmqRestPublishClient {
    private static final String PUBLISH_PATH = "/api/mqtt/publish";
    private final TbmqClient client;

    TbmqRestPublishClient(TbmqClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public static Builder builder(String baseUrl) { return new Builder(TbmqClient.builder(baseUrl)); }
    public static Builder builder(URI baseUri) { return new Builder(TbmqClient.builder(baseUri)); }

    public RestPublishResult publish(RestPublishRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            TbmqApiResponse<JsonNode> response = client.execute(TbmqApiRequest
                    .builder(TbmqHttpMethod.POST, PUBLISH_PATH).body(request).build(), JsonNode.class);
            JsonNode json = response.getBody();
            return new RestPublishResult(response.getStatusCode(), json.path("reasonCode").asInt(),
                    json.path("message").asText());
        } catch (TbmqApiException e) {
            throw new TbmqRestPublishException(e.getMessage(), e.getStatusCode(), e.getResponseBody());
        } catch (Exception e) {
            throw new TbmqRestPublishException("REST publish failed", e);
        }
    }

    public CompletableFuture<RestPublishResult> publishAsync(final RestPublishRequest request) {
        return CompletableFuture.supplyAsync(() -> publish(request), client.requireAsyncExecutor());
    }

    public ObjectMapper objectMapper() { return client.objectMapper(); }

    public static final class Builder {
        private final TbmqClient.Builder delegate;
        private Builder(TbmqClient.Builder delegate) { this.delegate = delegate; }
        public Builder accessToken(String value) { delegate.accessToken(value); return this; }
        public Builder credentials(String user, String secret) { delegate.credentials(user, secret); return this; }
        public Builder connectTimeout(Duration value) { delegate.connectTimeout(value); return this; }
        public Builder requestTimeout(Duration value) { delegate.requestTimeout(value); return this; }
        public Builder objectMapper(ObjectMapper value) { delegate.objectMapper(value); return this; }
        public Builder executor(Executor value) { delegate.executor(value); return this; }
        public TbmqRestPublishClient build() { return new TbmqRestPublishClient(delegate.build()); }
    }
}
