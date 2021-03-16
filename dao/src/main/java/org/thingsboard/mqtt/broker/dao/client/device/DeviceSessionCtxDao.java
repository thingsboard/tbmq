package org.thingsboard.mqtt.broker.dao.client.device;

import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;

import java.util.Collection;

public interface DeviceSessionCtxDao {
    void save(Collection<DeviceSessionCtx> deviceSessionContexts);

    Collection<DeviceSessionCtx> findAll(Collection<String> clientIds);
}
