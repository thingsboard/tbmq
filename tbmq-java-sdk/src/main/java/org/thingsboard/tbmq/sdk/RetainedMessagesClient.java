/**
 * Copyright © 2016-2026 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import org.thingsboard.tbmq.sdk.model.RetainedMessage;
import org.thingsboard.tbmq.sdk.model.TbmqPage;

public final class RetainedMessagesClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/retained-msg";
    RetainedMessagesClient(TbmqClient client) { super(client); }

    public RetainedMessage get(String topic) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH).query("topicName", topic).build(),
                RetainedMessage.class).getBody();
    }
    public TbmqPage<RetainedMessage> list(int pageSize, int page, String textSearch) {
        return client.execute(page(PATH, pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<RetainedMessage>>() { }).getBody();
    }
    public void delete(String topic) {
        client.execute(TbmqApiRequest.builder(TbmqHttpMethod.DELETE, PATH).query("topicName", topic).build(), Void.class);
    }
    public void clearEmptyTopicTrieNodes() { client.delete(PATH + "/topic-trie/clear"); }
}
