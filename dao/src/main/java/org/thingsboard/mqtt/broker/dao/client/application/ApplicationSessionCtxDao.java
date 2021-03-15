package org.thingsboard.mqtt.broker.dao.client.application;

import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;

import java.util.List;

public interface ApplicationSessionCtxDao {
    ApplicationSessionCtx save(ApplicationSessionCtx applicationSessionCtx);

    ApplicationSessionCtx find(String clientId);

    List<ApplicationSessionCtx> find();

    void remove(String clientId);

}
