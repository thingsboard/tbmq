/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

public final class RestPublishProperties {
    private Integer payloadFormatIndicator;
    private Integer messageExpiryInterval;
    private String contentType;
    private String responseTopic;
    private String correlationData;
    private Map<String, String> userProperties;

    public RestPublishProperties payloadFormatIndicator(int value) { this.payloadFormatIndicator = value; return this; }
    public RestPublishProperties messageExpiryInterval(int seconds) { this.messageExpiryInterval = seconds; return this; }
    public RestPublishProperties contentType(String value) { this.contentType = value; return this; }
    public RestPublishProperties responseTopic(String value) { this.responseTopic = value; return this; }
    public RestPublishProperties correlationData(byte[] value) {
        this.correlationData = java.util.Base64.getEncoder().encodeToString(value.clone()); return this;
    }
    public RestPublishProperties userProperty(String key, String value) {
        if (userProperties == null) userProperties = new LinkedHashMap<>();
        userProperties.put(key, value); return this;
    }

    public Integer getPayloadFormatIndicator() { return payloadFormatIndicator; }
    public Integer getMessageExpiryInterval() { return messageExpiryInterval; }
    public String getContentType() { return contentType; }
    public String getResponseTopic() { return responseTopic; }
    public String getCorrelationData() { return correlationData; }
    public Map<String, String> getUserProperties() {
        return userProperties == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(userProperties));
    }
}
