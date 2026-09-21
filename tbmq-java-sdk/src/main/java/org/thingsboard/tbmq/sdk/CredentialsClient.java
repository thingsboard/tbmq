/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.tbmq.sdk.model.MqttClientCredentials;
import org.thingsboard.tbmq.sdk.model.TbmqPage;

import java.util.Map;

public final class CredentialsClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/mqtt/client/credentials";
    CredentialsClient(TbmqClient client) { super(client); }

    public MqttClientCredentials save(MqttClientCredentials credentials) {
        return client.post(PATH, credentials, MqttClientCredentials.class);
    }
    public MqttClientCredentials get(String id) { return client.get(PATH + "/" + segment(id), MqttClientCredentials.class); }
    public MqttClientCredentials getByName(String name) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH).query("name", name).build(),
                MqttClientCredentials.class).getBody();
    }
    public TbmqPage<MqttClientCredentials> list(int pageSize, int page, String textSearch) {
        return client.execute(page(PATH, pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<MqttClientCredentials>>() { }).getBody();
    }
    public MqttClientCredentials changePassword(String id, String currentPassword, String newPassword) {
        Map<String, String> body = new java.util.LinkedHashMap<String, String>();
        body.put("currentPassword", currentPassword); body.put("newPassword", newPassword);
        return client.post(PATH + "/" + segment(id), body, MqttClientCredentials.class);
    }
    public Map<String, Long> stats() {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH + "/info").build(),
                new TypeReference<Map<String, Long>>() { }).getBody();
    }
    public void delete(String id) { client.delete(PATH + "/" + segment(id)); }
}
