/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.tbmq.sdk.model.MqttAuthProvider;
import org.thingsboard.tbmq.sdk.model.TbmqPage;

public final class AuthProvidersClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/mqtt/auth/provider";
    AuthProvidersClient(TbmqClient client) { super(client); }

    public MqttAuthProvider save(MqttAuthProvider provider) { return client.post(PATH, provider, MqttAuthProvider.class); }
    public MqttAuthProvider get(String id) { return client.get(PATH + "/" + segment(id), MqttAuthProvider.class); }
    public MqttAuthProvider getByType(String type) { return client.get(PATH + "/type/" + segment(type), MqttAuthProvider.class); }
    public TbmqPage<MqttAuthProvider> list(int pageSize, int page, String textSearch) {
        return client.execute(page("/api/mqtt/auth/providers", pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<MqttAuthProvider>>() { }).getBody();
    }
    public void enable(String id) { client.post(PATH + "/" + segment(id) + "/enable", null, Void.class); }
    public void disable(String id) { client.post(PATH + "/" + segment(id) + "/disable", null, Void.class); }
    public void checkHttp(MqttAuthProvider provider) { client.post(PATH + "/http/check", provider, Void.class); }
    public String basicStrategy() { return client.get(PATH + "/basic/strategy", String.class); }
}
