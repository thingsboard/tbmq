/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.tbmq.sdk.model.ClientSession;
import org.thingsboard.tbmq.sdk.model.TbmqPage;

import java.util.Map;

public final class SessionsClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/client-session";
    SessionsClient(TbmqClient client) { super(client); }

    public ClientSession get(String clientId) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH).query("clientId", clientId).build(),
                ClientSession.class).getBody();
    }
    public TbmqPage<ClientSession> list(int pageSize, int page, String textSearch) {
        return client.execute(page(PATH, pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<ClientSession>>() { }).getBody();
    }
    public Map<String, Long> stats() {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH + "/info").build(),
                new TypeReference<Map<String, Long>>() { }).getBody();
    }
    public void disconnect(String clientId, String sessionId) { sessionAction("disconnect", clientId, sessionId); }
    public void remove(String clientId, String sessionId) { sessionAction("remove", clientId, sessionId); }
    private void sessionAction(String action, String clientId, String sessionId) {
        client.execute(TbmqApiRequest.builder(TbmqHttpMethod.DELETE, PATH + "/" + action)
                .query("clientId", clientId).query("sessionId", sessionId).build(), Void.class);
    }
}
