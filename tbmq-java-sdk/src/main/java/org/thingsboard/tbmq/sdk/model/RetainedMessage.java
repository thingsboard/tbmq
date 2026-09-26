/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RetainedMessage {
    private String topic;
    private String payload;
    private int qos;
    private long createdTime;
    private JsonNode userProperties;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    public JsonNode getUserProperties() { return userProperties; }
    public void setUserProperties(JsonNode userProperties) { this.userProperties = userProperties; }
}
