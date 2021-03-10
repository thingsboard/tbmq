package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;
import org.thingsboard.mqtt.broker.common.data.PublishedMsgInfo;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInFlightMessagesServiceImpl implements DeviceInFlightMessagesService {
    private static final DeviceSessionCtx EMPTY_DEVICE_SESSION_CTX = DeviceSessionCtx.builder().publishedMsgInfos(Collections.emptyList()).build();

    private final DeviceSessionCtxService deviceSessionCtxService;

    // TODO: clear old saved clients
    private final ConcurrentMap<String, ConcurrentMap<Integer, PublishedMsgInfo>> unacknowledgedMessages = new ConcurrentHashMap<>();

    @PreDestroy
    public void destroy() {
        log.info("Trying to persist unacknowledged messages for {} devices.", unacknowledgedMessages.size());
        unacknowledgedMessages.forEach((clientId, clientMessages) -> {
            DeviceSessionCtx deviceSessionCtx = DeviceSessionCtx.builder()
                    .clientId(clientId)
                    .publishedMsgInfos(clientMessages.values())
                    .build();
            try {
                deviceSessionCtxService.saveDeviceSessionCtx(deviceSessionCtx);
            } catch (Exception e) {
                log.warn("[{}] Failed to persisted unacknowledged for client.", clientId);
            }
        });
        log.info("Finished persisting unacknowledged messages.");
    }

    @Override
    public void msgPublished(String clientId, PublishedMsgInfo publishedMsgInfo) {
        ConcurrentMap<Integer, PublishedMsgInfo> clientMessages = getClientMessages(clientId);
        clientMessages.put(publishedMsgInfo.getPacketId(), publishedMsgInfo);
    }

    @Override
    public PublishedMsgInfo msgAcknowledged(String clientId, int packetId) {
        ConcurrentMap<Integer, PublishedMsgInfo> clientMessages = getClientMessages(clientId);
        return clientMessages.get(packetId);
    }

    @Override
    public List<PublishedMsgInfo> getUnacknowledgedMessages(String clientId) {
        ConcurrentMap<Integer, PublishedMsgInfo> clientMessages = getClientMessages(clientId);
        return new ArrayList<>(clientMessages.values());
    }

    @Override
    public void clientConnected(String clientId) {
        DeviceSessionCtx deviceSessionCtx = deviceSessionCtxService.getDeviceSessionCtx(clientId).orElse(EMPTY_DEVICE_SESSION_CTX);
        ConcurrentHashMap<Integer, PublishedMsgInfo> clientMessages = new ConcurrentHashMap<>();
        for (PublishedMsgInfo publishedMsgInfo : deviceSessionCtx.getPublishedMsgInfos()) {
            clientMessages.put(publishedMsgInfo.getPacketId(), publishedMsgInfo);
        }
        unacknowledgedMessages.put(clientId, clientMessages);
    }

    @Override
    public void clientDisconnected(String clientId) {
        ConcurrentMap<Integer, PublishedMsgInfo> clientMessages = unacknowledgedMessages.remove(clientId);
        if (clientMessages == null) {
            log.warn("[{}] Unacknowledged messages map is null.", clientId);
            return;
        }
        DeviceSessionCtx deviceSessionCtx = DeviceSessionCtx.builder()
                .clientId(clientId)
                .publishedMsgInfos(clientMessages.values())
                .build();
        deviceSessionCtxService.saveDeviceSessionCtx(deviceSessionCtx);
    }

    @Override
    public void clearInFlightMessages(String clientId) {
        unacknowledgedMessages.remove(clientId);
        deviceSessionCtxService.deleteDeviceSessionCtx(clientId);
    }

    private ConcurrentMap<Integer, PublishedMsgInfo> getClientMessages(String clientId) {
        ConcurrentMap<Integer, PublishedMsgInfo> clientMessages = unacknowledgedMessages.get(clientId);
        if (clientMessages == null) {
            throw new IllegalStateException("No unacknowledged messages map is stored for client.");
        }
        return clientMessages;
    }
}
