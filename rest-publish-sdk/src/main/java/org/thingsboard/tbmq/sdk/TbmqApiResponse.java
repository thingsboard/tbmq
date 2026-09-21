/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TbmqApiResponse<T> {
    private final int statusCode;
    private final T body;
    private final Map<String, List<String>> headers;

    TbmqApiResponse(int statusCode, T body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
    }

    public int getStatusCode() { return statusCode; }
    public T getBody() { return body; }
    public Map<String, List<String>> getHeaders() { return headers; }
}
