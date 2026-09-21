/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

public final class RestPublishResult {
    private final int httpStatus;
    private final int reasonCode;
    private final String message;

    public RestPublishResult(int httpStatus, int reasonCode, String message) {
        this.httpStatus = httpStatus;
        this.reasonCode = reasonCode;
        this.message = message;
    }

    public int getHttpStatus() { return httpStatus; }
    public int getReasonCode() { return reasonCode; }
    public String getMessage() { return message; }
    public boolean hasMatchingSubscribers() { return reasonCode == 0; }
    public boolean isAccepted() { return httpStatus == 200 || httpStatus == 202; }
}
