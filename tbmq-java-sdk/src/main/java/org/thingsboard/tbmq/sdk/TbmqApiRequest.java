/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TbmqApiRequest {
    private final TbmqHttpMethod method;
    private final String path;
    private final Map<String, String> queryParameters;
    private final Map<String, String> headers;
    private final Object body;
    private final boolean authenticated;

    private TbmqApiRequest(Builder builder) {
        method = builder.method;
        path = builder.path;
        queryParameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.queryParameters));
        headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.headers));
        body = builder.body;
        authenticated = builder.authenticated;
    }

    public static Builder builder(TbmqHttpMethod method, String path) {
        return new Builder(method, path);
    }

    public TbmqHttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public Map<String, String> getQueryParameters() { return queryParameters; }
    public Map<String, String> getHeaders() { return headers; }
    public Object getBody() { return body; }
    public boolean isAuthenticated() { return authenticated; }

    public static final class Builder {
        private final TbmqHttpMethod method;
        private final String path;
        private final Map<String, String> queryParameters = new LinkedHashMap<String, String>();
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private Object body;
        private boolean authenticated = true;

        private Builder(TbmqHttpMethod method, String path) {
            this.method = Objects.requireNonNull(method, "method");
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                throw new IllegalArgumentException("path must start with one '/'");
            }
            this.path = path;
        }

        public Builder query(String name, Object value) {
            if (value != null) queryParameters.put(requireName(name), String.valueOf(value));
            return this;
        }

        public Builder header(String name, String value) {
            headers.put(requireName(name), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder body(Object value) { body = value; return this; }
        public Builder authenticated(boolean value) { authenticated = value; return this; }
        public TbmqApiRequest build() { return new TbmqApiRequest(this); }

        private static String requireName(String value) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("name must not be blank");
            return value;
        }
    }
}
