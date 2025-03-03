/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.AbstractIntegration;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.BasicCredentials;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.CredentialsType;

import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MQTT_PROTOCOL_NAME;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MQTT_V_3_1_PROTOCOL_NAME;

@Slf4j
public class MqttIntegration extends AbstractIntegration {

    private MqttIntegrationConfig config;
    private MqttClient client;

    @Override
    public void doValidateConfiguration(JsonNode clientConfiguration, boolean allowLocalNetworkHosts) throws ThingsboardException {
        try {
            MqttIntegrationConfig mqttIntegrationConfig = getClientConfiguration(clientConfiguration, MqttIntegrationConfig.class);
            MqttConfigValidator.validate(mqttIntegrationConfig);
            if (!allowLocalNetworkHosts && isLocalNetworkHost(mqttIntegrationConfig.getHost())) {
                throw new IllegalArgumentException("Usage of local network host for MQTT broker connection is not allowed!");
            }
        } catch (Exception e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void doCheckConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {
        this.context = ctx;
        try {
            MqttIntegrationConfig mqttIntegrationConfig = getClientConfiguration(integration, MqttIntegrationConfig.class);

            if (mqttIntegrationConfig.getConnectTimeoutSec() > ctx.getIntegrationConnectTimeoutSec() && ctx.getIntegrationConnectTimeoutSec() > 0) {
                log.debug("[{}] Reduce MQTT integration connection timeout (s) to the limit [{}]", integration.getId(), mqttIntegrationConfig.getConnectTimeoutSec());
                mqttIntegrationConfig.setConnectTimeoutSec(ctx.getIntegrationConnectTimeoutSec());
            }
            MqttClient mqttClient = null;
            try {
                mqttClient = initClient(mqttIntegrationConfig);
            } finally {
                if (mqttClient != null) {
                    mqttClient.disconnect();
                }
            }
            ctx.getCheckConnectionCallback().onSuccess();
        } catch (Exception e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        config = getClientConfiguration(lifecycleMsg, MqttIntegrationConfig.class);
        try {
            this.client = initClient(config);
        } catch (Exception e) {
            this.client.disconnect();
            throw e;
        }
        startProcessingIntegrationMessages(this);
    }

    @Override
    public void doStopProcessingPersistedMessages() {
        if (this.client != null) {
            try {
                this.client.disconnect();
            } catch (Exception e) {
                log.error("[{}][{}] Failed to disconnect MQTT client", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName(), e);
            }
        }
    }

    @Override
    public void process(PublishIntegrationMsgProto msg, IntegrationMsgCallback callback) {
        client.publish(config.getTopicName(), Unpooled.wrappedBuffer(constructValue(msg).getBytes(StandardCharsets.UTF_8)),
                        MqttQoS.valueOf(config.getQos()), config.isRetained())
                .addListener(future -> {
                            if (future.isSuccess()) {
                                log.debug("[{}][{}] processPublish success {}", getId(), getName(), config.getTopicName());
                                integrationStatistics.incMessagesProcessed();
                                callback.onSuccess();
                            } else {
                                var t = future.cause();
                                log.warn("[{}][{}] processException", getId(), getName(), t);
                                handleMsgProcessingFailure(t);
                                callback.onFailure(t);
                            }
                        }
                );
    }

    private UUID getId() {
        return context.getLifecycleMsg().getIntegrationId();
    }

    private String getName() {
        return context.getLifecycleMsg().getName();
    }

    private MqttClient initClient(MqttIntegrationConfig mqttIntegrationConfig) throws Exception {
        MqttClientConfig clientConfig = new MqttClientConfig(getSslContext(mqttIntegrationConfig));
        clientConfig.setOwnerId("tbmq");
        clientConfig.setClientId(mqttIntegrationConfig.getClientId());
        clientConfig.setTimeoutSeconds(mqttIntegrationConfig.getKeepAliveSec());
        clientConfig.setProtocolVersion(getMqttVersion(mqttIntegrationConfig));
        prepareAuthConfigWhenBasic(mqttIntegrationConfig, clientConfig);
        clientConfig.setReconnect(mqttIntegrationConfig.getReconnectPeriodSec() != 0);
        clientConfig.setReconnectDelay(mqttIntegrationConfig.getReconnectPeriodSec());

        MqttClient client = getMqttClient(clientConfig);
        client.setEventLoop(context.getSharedEventLoop());
        Promise<MqttConnectResult> connectFuture = client.connect(mqttIntegrationConfig.getHost(), mqttIntegrationConfig.getPort());
        MqttConnectResult result;
        try {
            result = connectFuture.get(mqttIntegrationConfig.getConnectTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            connectFuture.cancel(true);
            String hostPort = mqttIntegrationConfig.getHost() + ":" + mqttIntegrationConfig.getPort();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s.", hostPort));
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            String hostPort = mqttIntegrationConfig.getHost() + ":" + mqttIntegrationConfig.getPort();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s. Result code is: %s", hostPort, result.getReturnCode()));
        }
        return client;
    }

    private SslContext getSslContext(MqttIntegrationConfig mqttIntegrationConfig) throws SSLException {
        return mqttIntegrationConfig.isSsl() ? mqttIntegrationConfig.getCredentials().initSslContext() : null;
    }

    private MqttVersion getMqttVersion(MqttIntegrationConfig mqttIntegrationConfig) {
        var version = (byte) mqttIntegrationConfig.getMqttVersion();
        var protocolName = version > 3 ? MQTT_PROTOCOL_NAME : MQTT_V_3_1_PROTOCOL_NAME;
        return MqttVersion.fromProtocolNameAndLevel(protocolName, version);
    }

    private void prepareAuthConfigWhenBasic(MqttIntegrationConfig integrationConfig, MqttClientConfig clientConfig) {
        ClientCredentials credentials = integrationConfig.getCredentials();
        if (CredentialsType.BASIC == credentials.getType()) {
            BasicCredentials basicCredentials = (BasicCredentials) credentials;
            clientConfig.setUsername(basicCredentials.getUsername());
            clientConfig.setPassword(basicCredentials.getPassword());
        }
    }

    MqttClient getMqttClient(MqttClientConfig clientConfig) {
        return MqttClient.create(clientConfig, null, null);
    }

}
