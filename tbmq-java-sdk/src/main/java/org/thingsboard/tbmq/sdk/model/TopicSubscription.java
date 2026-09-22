/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Read model returned by the client-subscription endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicSubscription {
    private String topicFilter;
    private int qos;
    private String shareName;
    private JsonNode options;
    private int subscriptionId = -1;

    public String getTopicFilter() { return topicFilter; }
    public void setTopicFilter(String topicFilter) { this.topicFilter = topicFilter; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
    public String getShareName() { return shareName; }
    public void setShareName(String shareName) { this.shareName = shareName; }
    public JsonNode getOptions() { return options; }
    public void setOptions(JsonNode options) { this.options = options; }
    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    /** Converts the read representation into the DTO accepted by the update endpoint. */
    public Subscription toUpdateSubscription() {
        Subscription result = new Subscription();
        result.setTopicFilter(shareName == null || shareName.isEmpty()
                ? topicFilter : "$share/" + shareName + "/" + topicFilter);
        result.setQos(qosName(qos));
        result.setOptions(updateOptions(options));
        result.setSubscriptionId(subscriptionId < 0 ? null : subscriptionId);
        return result;
    }

    private static String qosName(int value) {
        switch (value) {
            case 0: return "AT_MOST_ONCE";
            case 1: return "AT_LEAST_ONCE";
            case 2: return "EXACTLY_ONCE";
            default: throw new IllegalArgumentException("Invalid QoS: " + value);
        }
    }

    private static JsonNode updateOptions(JsonNode source) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("noLocal", source != null && source.path("noLocal").asBoolean(false));
        result.put("retainAsPublish", source != null && source.path("retainAsPublish").asBoolean(false));
        JsonNode retainHandling = source == null ? null : source.get("retainHandling");
        result.put("retainHandling", retainHandlingValue(retainHandling));
        return result;
    }

    private static int retainHandlingValue(JsonNode value) {
        if (value == null || value.isNull()) return 0;
        if (value.isInt()) return value.asInt();
        String name = value.asText();
        if ("SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS".equals(name)) return 1;
        if ("DONT_SEND_AT_SUBSCRIBE".equals(name)) return 2;
        return 0;
    }
}
