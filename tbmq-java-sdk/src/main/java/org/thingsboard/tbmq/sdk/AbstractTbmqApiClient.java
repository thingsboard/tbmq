/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

abstract class AbstractTbmqApiClient {
    protected final TbmqClient client;

    AbstractTbmqApiClient(TbmqClient client) { this.client = client; }

    protected TbmqApiRequest.Builder page(String path, int pageSize, int page, String textSearch) {
        if (pageSize < 1 || page < 0) throw new IllegalArgumentException("pageSize must be positive and page must not be negative");
        return TbmqApiRequest.builder(TbmqHttpMethod.GET, path)
                .query("pageSize", pageSize).query("page", page).query("textSearch", textSearch);
    }

    protected static String segment(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("path value must not be blank");
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
}
