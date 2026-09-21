/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Subscription {
    private String topicFilter;
    private String qos;
    private JsonNode options;
    private Integer subscriptionId;

    public String getTopicFilter() { return topicFilter; }
    public void setTopicFilter(String topicFilter) { this.topicFilter = topicFilter; }
    public String getQos() { return qos; }
    public void setQos(String qos) { this.qos = qos; }
    public JsonNode getOptions() { return options; }
    public void setOptions(JsonNode options) { this.options = options; }
    public Integer getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Integer subscriptionId) { this.subscriptionId = subscriptionId; }
}
