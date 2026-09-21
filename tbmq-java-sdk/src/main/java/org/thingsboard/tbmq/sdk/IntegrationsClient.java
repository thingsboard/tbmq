/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.tbmq.sdk.model.Integration;
import org.thingsboard.tbmq.sdk.model.TbmqPage;

public final class IntegrationsClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/integration";
    IntegrationsClient(TbmqClient client) { super(client); }

    public Integration save(Integration integration) { return client.post(PATH, integration, Integration.class); }
    public Integration get(String id) { return client.get(PATH + "/" + segment(id), Integration.class); }
    public TbmqPage<Integration> list(int pageSize, int page, String textSearch) {
        return client.execute(page("/api/integrations", pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<Integration>>() { }).getBody();
    }
    public void check(Integration integration) { client.post(PATH + "/check", integration, Void.class); }
    public void restart(String id) { client.post(PATH + "/" + segment(id), null, Void.class); }
    public void delete(String id) { client.delete(PATH + "/" + segment(id)); }
}
