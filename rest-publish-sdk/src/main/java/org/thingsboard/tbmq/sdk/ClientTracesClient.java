/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.tbmq.sdk.model.ClientTraceConfig;

import java.util.List;

public final class ClientTracesClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/client-traces";
    ClientTracesClient(TbmqClient client) { super(client); }

    public List<ClientTraceConfig> list() {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH).build(),
                new TypeReference<List<ClientTraceConfig>>() { }).getBody();
    }
    public ClientTraceConfig create(ClientTraceConfig config) {
        return client.post(PATH, config, ClientTraceConfig.class);
    }
    public List<JsonNode> events(String clientId, long from, long to, int limit) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH + "/" + segment(clientId) + "/events")
                .query("from", from).query("to", to).query("limit", limit).build(),
                new TypeReference<List<JsonNode>>() { }).getBody();
    }
    public void delete(String traceId) { client.delete(PATH + "/" + segment(traceId)); }
}
