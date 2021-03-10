package org.thingsboard.mqtt.broker.dao.client.device;

import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;

import java.util.List;

public interface DeviceSessionCtxDao {
    DeviceSessionCtx save(DeviceSessionCtx deviceSessionCtx);

    DeviceSessionCtx find(String clientId);

    List<DeviceSessionCtx> find();

    void remove(String clientId);

}
