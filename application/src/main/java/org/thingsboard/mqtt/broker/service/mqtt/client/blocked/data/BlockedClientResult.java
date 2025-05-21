package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data;

import lombok.Data;

@Data
public class BlockedClientResult {

    private final boolean isBlocked;
    private final BlockedClient blockedClient;

    public static BlockedClientResult blocked(BlockedClient blockedClient) {
        return new BlockedClientResult(true, blockedClient);
    }

    public static BlockedClientResult notBlocked() {
        return new BlockedClientResult(false, null);
    }

    public String getKey() {
        return blockedClient.getKey();
    }
}
