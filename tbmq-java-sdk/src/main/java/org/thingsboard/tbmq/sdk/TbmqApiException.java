/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

public class TbmqApiException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public TbmqApiException(String message, Throwable cause) {
        super(message, cause);
        statusCode = -1;
        responseBody = null;
    }

    public TbmqApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
}
