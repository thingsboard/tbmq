/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientSession {
    private String id;
    private String clientId;
    private String sessionId;
    private String connectionState;
    private String clientType;
    private String nodeId;
    private boolean cleanStart;
    private int subscriptionsCount;
    private List<Subscription> subscriptions;
    private long connectedAt;
    private long disconnectedAt;
    private String clientIpAdr;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getConnectionState() { return connectionState; }
    public void setConnectionState(String connectionState) { this.connectionState = connectionState; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public boolean isCleanStart() { return cleanStart; }
    public void setCleanStart(boolean cleanStart) { this.cleanStart = cleanStart; }
    public int getSubscriptionsCount() { return subscriptionsCount; }
    public void setSubscriptionsCount(int subscriptionsCount) { this.subscriptionsCount = subscriptionsCount; }
    public List<Subscription> getSubscriptions() { return subscriptions; }
    public void setSubscriptions(List<Subscription> subscriptions) { this.subscriptions = subscriptions; }
    public long getConnectedAt() { return connectedAt; }
    public void setConnectedAt(long connectedAt) { this.connectedAt = connectedAt; }
    public long getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(long disconnectedAt) { this.disconnectedAt = disconnectedAt; }
    public String getClientIpAdr() { return clientIpAdr; }
    public void setClientIpAdr(String clientIpAdr) { this.clientIpAdr = clientIpAdr; }
}
