/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

public class TbmqRestPublishException extends TbmqApiException {

    public TbmqRestPublishException(String message, int statusCode, String responseBody) {
        super(message, statusCode, responseBody);
    }
    public TbmqRestPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
