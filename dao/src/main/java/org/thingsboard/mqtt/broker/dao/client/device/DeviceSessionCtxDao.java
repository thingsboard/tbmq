package org.thingsboard.mqtt.broker.dao.client.device;

import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;

import java.util.List;

public interface DeviceSessionCtxDao {
    void save(List<DeviceSessionCtx> deviceSessionCtxList);

    List<DeviceSessionCtx> findAll(List<String> clientIds);
}
