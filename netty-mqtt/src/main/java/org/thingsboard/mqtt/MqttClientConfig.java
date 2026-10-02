/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt;

import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.util.Random;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class MqttClientConfig {

    /**
     * The largest keep-alive MQTT can carry: CONNECT holds it in two bytes.
     */
    public static final int MAX_KEEP_ALIVE_SECONDS = 65535;

    @Getter
    private final SslContext sslContext;
    private final String randomClientId;

    @Getter
    @Setter
    private String ownerId; // [TenantId][IntegrationId] or [TenantId][RuleNodeId] for exceptions logging purposes
    @Nonnull
    @Getter
    private String clientId;
    @Getter
    private int timeoutSeconds = 60;
    @Getter
    private MqttVersion protocolVersion = MqttVersion.MQTT_3_1;
    @Nullable
    @Getter
    @Setter
    private String username = null;
    @Nullable
    @Getter
    @Setter
    private String password = null;
    @Getter
    @Setter
    private boolean cleanSession = true;
    @Nullable
    @Getter
    @Setter
    private MqttLastWill lastWill;
    @Setter
    @Getter
    private Class<? extends Channel> channelClass = NioSocketChannel.class;

    @Getter
    @Setter
    private boolean reconnect = true;
    @Getter
    private long reconnectDelay = 1L;
    @Getter
    private int maxBytesInMessage = 32368;

    @Getter
    private int connectTimeoutSec = 30;

    @Getter
    private int backPressureHighWatermark = 450;
    @Getter
    private int backPressureLowWatermark = 200;

    @Nonnull
    @Getter
    private RetransmissionConfig retransmissionConfig = new RetransmissionConfig(3, 5000L, 0.15d);

    public record RetransmissionConfig(int maxAttempts, long initialDelayMillis, double jitterFactor) {

        public RetransmissionConfig {
            if (maxAttempts < 0) {
                throw new IllegalArgumentException("Max retransmission attempts (maxAttempts) must be zero or greater, but was " + maxAttempts);
            }
            if (initialDelayMillis < 0) {
                throw new IllegalArgumentException("Initial retransmission delay (initialDelayMillis) must be zero or greater, but was " + initialDelayMillis);
            }
            if (jitterFactor < 0) {
                throw new IllegalArgumentException("Jitter factor (jitterFactor) must be zero or greater, but was " + jitterFactor);
            }
        }

    }

    public MqttClientConfig() {
        this(null);
    }

    public MqttClientConfig(SslContext sslContext) {
        this.sslContext = sslContext;
        Random random = new Random();
        StringBuilder id = new StringBuilder("netty-mqtt/");
        String[] options = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("");
        for (int i = 0; i < 8; i++) {
            id.append(options[random.nextInt(options.length)]);
        }
        this.clientId = id.toString();
        this.randomClientId = id.toString();
    }

    public void setClientId(@Nullable String clientId) {
        if (clientId == null) {
            this.clientId = randomClientId;
        } else {
            this.clientId = clientId;
        }
    }

    public void setRetransmissionConfig(@Nonnull RetransmissionConfig retransmissionConfig) {
        if (retransmissionConfig == null) {
            throw new NullPointerException("retransmissionConfig");
        }
        this.retransmissionConfig = retransmissionConfig;
    }

    /**
     * Sets the keep-alive in seconds: sent in CONNECT, and the idle time after which the client pings the server.
     *
     * @param timeoutSeconds 1 to 65535, or 0 to disable keep-alive - CONNECT then carries 0 and the client never pings.
     *                       -1, which callers used to mean "off", is stored as 0.
     * @throws IllegalArgumentException for any other value
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        int keepAlive = timeoutSeconds == -1 ? 0 : timeoutSeconds;
        if (keepAlive < 0 || keepAlive > MAX_KEEP_ALIVE_SECONDS) {
            throw new IllegalArgumentException("timeoutSeconds must be between 0 (keep-alive off) and "
                    + MAX_KEEP_ALIVE_SECONDS + ", but was " + timeoutSeconds);
        }
        this.timeoutSeconds = keepAlive;
    }

    public void setBackPressureHighWatermark(int backPressureHighWatermark) {
        if (backPressureHighWatermark < 0) {
            throw new IllegalArgumentException("backPressureHighWatermark must be >= 0 (0 to disable), but was " + backPressureHighWatermark);
        }
        if (backPressureHighWatermark > 0 && backPressureLowWatermark > 0 && backPressureHighWatermark <= backPressureLowWatermark) {
            throw new IllegalArgumentException("backPressureHighWatermark (" + backPressureHighWatermark + ") must be > backPressureLowWatermark (" + backPressureLowWatermark + ")");
        }
        this.backPressureHighWatermark = backPressureHighWatermark;
    }

    public void setBackPressureLowWatermark(int backPressureLowWatermark) {
        if (backPressureLowWatermark < 0) {
            throw new IllegalArgumentException("backPressureLowWatermark must be >= 0 (0 to disable), but was " + backPressureLowWatermark);
        }
        if (backPressureLowWatermark > 0 && backPressureHighWatermark > 0 && backPressureLowWatermark >= backPressureHighWatermark) {
            throw new IllegalArgumentException("backPressureLowWatermark (" + backPressureLowWatermark + ") must be < backPressureHighWatermark (" + backPressureHighWatermark + ")");
        }
        this.backPressureLowWatermark = backPressureLowWatermark;
    }

    public boolean isBackPressureEnabled() {
        return backPressureHighWatermark > 0 && backPressureLowWatermark > 0;
    }

    public void setProtocolVersion(MqttVersion protocolVersion) {
        if (protocolVersion == null) {
            throw new NullPointerException("protocolVersion");
        }
        this.protocolVersion = protocolVersion;
    }

    /**
     * Sets the reconnect delay in seconds. Defaults to 1 second.
     * @param reconnectDelay
     * @throws IllegalArgumentException if reconnectDelay is smaller than 1.
     */
    public void setReconnectDelay(long reconnectDelay) {
        if (reconnectDelay <= 0) {
            throw new IllegalArgumentException("reconnectDelay must be > 0");
        }
        this.reconnectDelay = reconnectDelay;
    }

    /**
     * Sets how long one connect attempt may take, from the start of its TCP connect to its CONNACK, TLS handshake
     * included. On expiry the channel is closed and the attempt's future fails with a
     * {@link io.netty.channel.ConnectTimeoutException}; an automatic reconnect then proceeds as after any failed attempt.
     * Defaults to 30 seconds, netty's own TCP connect timeout.
     *
     * @throws IllegalArgumentException if connectTimeoutSec is not positive
     */
    public void setConnectTimeoutSec(int connectTimeoutSec) {
        if (connectTimeoutSec <= 0) {
            throw new IllegalArgumentException("connectTimeoutSec must be > 0, but was " + connectTimeoutSec);
        }
        this.connectTimeoutSec = connectTimeoutSec;
    }

    /**
     * Sets the largest packet the client reads, counted as its remaining length. A PUBLISH over it is skipped without
     * being buffered, acked as failed and reported to {@link MqttClientCallback#onPublishTooLarge}; any other packet
     * over it closes the connection. The absolute maximum is 256 MB, as set by the MQTT spec.
     *
     * @param maxBytesInMessage
     * @throws IllegalArgumentException if maxBytesInMessage is smaller than 1 or greater than 256_000_000.
     */
    public void setMaxBytesInMessage(int maxBytesInMessage) {
        if (maxBytesInMessage <= 0 || maxBytesInMessage > 256_000_000) {
            throw new IllegalArgumentException("maxBytesInMessage must be > 0 or < 256_000_000");
        }
        this.maxBytesInMessage = maxBytesInMessage;
    }

}
