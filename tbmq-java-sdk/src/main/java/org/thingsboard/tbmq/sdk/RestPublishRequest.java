/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Base64;
import java.util.Objects;

public final class RestPublishRequest {
    private final String topic;
    private final JsonNode payload;
    private final RestPublishEncoding payloadEncoding;
    private final int qos;
    private final boolean retain;
    private final RestPublishProperties properties;

    private RestPublishRequest(Builder builder) {
        topic = builder.topic;
        payload = builder.payload;
        payloadEncoding = builder.payloadEncoding;
        qos = builder.qos;
        retain = builder.retain;
        properties = builder.properties;
    }

    public static Builder text(String topic, String payload) {
        return new Builder(topic, TextNode.valueOf(Objects.requireNonNull(payload)), RestPublishEncoding.TEXT);
    }

    public static Builder bytes(String topic, byte[] payload) {
        return new Builder(topic, TextNode.valueOf(Base64.getEncoder().encodeToString(payload.clone())), RestPublishEncoding.BASE64);
    }

    public static Builder json(String topic, JsonNode payload) {
        return new Builder(topic, Objects.requireNonNull(payload), RestPublishEncoding.JSON);
    }

    public static Builder json(String topic, String json, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        try { return json(topic, mapper.readTree(json)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JSON payload", e); }
    }

    public String getTopic() { return topic; }
    public JsonNode getPayload() { return payload; }
    public RestPublishEncoding getPayloadEncoding() { return payloadEncoding; }
    public int getQos() { return qos; }
    public boolean isRetain() { return retain; }
    public RestPublishProperties getProperties() { return properties; }

    public static final class Builder {
        private final String topic;
        private final JsonNode payload;
        private final RestPublishEncoding payloadEncoding;
        private int qos;
        private boolean retain;
        private RestPublishProperties properties;

        private Builder(String topic, JsonNode payload, RestPublishEncoding payloadEncoding) {
            if (topic == null || topic.trim().isEmpty()) throw new IllegalArgumentException("topic must not be blank");
            this.topic = topic; this.payload = payload; this.payloadEncoding = payloadEncoding;
        }
        public Builder qos(int value) {
            if (value < 0 || value > 2) throw new IllegalArgumentException("qos must be 0, 1 or 2");
            qos = value; return this;
        }
        public Builder retain(boolean value) { retain = value; return this; }
        public Builder properties(RestPublishProperties value) { properties = value; return this; }
        public RestPublishRequest build() { return new RestPublishRequest(this); }
    }
}
