/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Integration {
    private String id;
    private long createdTime;
    private String name;
    private String type;
    private boolean enabled;
    private long disconnectedTime;
    private JsonNode configuration;
    private JsonNode additionalInfo;
    private JsonNode status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getDisconnectedTime() { return disconnectedTime; }
    public void setDisconnectedTime(long disconnectedTime) { this.disconnectedTime = disconnectedTime; }
    public JsonNode getConfiguration() { return configuration; }
    public void setConfiguration(JsonNode configuration) { this.configuration = configuration; }
    public JsonNode getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(JsonNode additionalInfo) { this.additionalInfo = additionalInfo; }
    public JsonNode getStatus() { return status; }
    public void setStatus(JsonNode status) { this.status = status; }
}
