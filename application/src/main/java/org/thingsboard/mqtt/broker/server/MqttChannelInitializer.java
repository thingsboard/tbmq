package org.thingsboard.mqtt.broker.server;

import io.netty.handler.ssl.SslHandler;

public interface MqttChannelInitializer {

    int getMaxPayloadSize();

    int getMaxClientIdLength();

    String getChannelInitializerName();

    default SslHandler getSslHandler() {
        return null;
    }

}
