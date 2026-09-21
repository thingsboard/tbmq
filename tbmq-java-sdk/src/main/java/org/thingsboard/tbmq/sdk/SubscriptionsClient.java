/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.tbmq.sdk.model.ClientSubscription;
import org.thingsboard.tbmq.sdk.model.ClientSubscriptions;
import org.thingsboard.tbmq.sdk.model.Subscription;
import org.thingsboard.tbmq.sdk.model.TbmqPage;

import java.util.List;

public final class SubscriptionsClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/subscription";
    SubscriptionsClient(TbmqClient client) { super(client); }

    public ClientSubscriptions update(ClientSubscriptions subscriptions) {
        return client.post(PATH, subscriptions, ClientSubscriptions.class);
    }
    public List<Subscription> getForClient(String clientId) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH)
                .query("clientId", clientId).build(), new TypeReference<List<Subscription>>() { }).getBody();
    }
    public TbmqPage<ClientSubscription> list(int pageSize, int page, String textSearch) {
        return client.execute(page(PATH + "/all", pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<ClientSubscription>>() { }).getBody();
    }
    public void clearEmptyTopicTrieNodes() { client.delete(PATH + "/topic-trie/clear"); }
}
