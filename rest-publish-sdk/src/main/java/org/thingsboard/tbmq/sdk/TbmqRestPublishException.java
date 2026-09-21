/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

public class TbmqRestPublishException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public TbmqRestPublishException(String message, int statusCode, String responseBody) {
        super(message); this.statusCode = statusCode; this.responseBody = responseBody;
    }
    public TbmqRestPublishException(String message, Throwable cause) {
        super(message, cause); this.statusCode = -1; this.responseBody = null;
    }
    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
}
