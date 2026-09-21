/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttClientCredentials {
    private String id;
    private long createdTime;
    private String credentialsId;
    private String name;
    private String clientType;
    private String credentialsType;
    private String credentialsValue;
    private JsonNode additionalInfo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    public String getCredentialsId() { return credentialsId; }
    public void setCredentialsId(String credentialsId) { this.credentialsId = credentialsId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public String getCredentialsType() { return credentialsType; }
    public void setCredentialsType(String credentialsType) { this.credentialsType = credentialsType; }
    public String getCredentialsValue() { return credentialsValue; }
    public void setCredentialsValue(String credentialsValue) { this.credentialsValue = credentialsValue; }
    public JsonNode getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(JsonNode additionalInfo) { this.additionalInfo = additionalInfo; }
}
