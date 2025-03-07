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
package org.thingsboard.mqtt.broker.integration.service.integration.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;

@Slf4j
public class HttpIntegration extends AbstractHttpIntegration {

    private TbHttpClient tbHttpClient;
    private HttpIntegrationConfig config;

    @Override
    public void doValidateConfiguration(JsonNode clientConfiguration, boolean allowLocalNetworkHosts) throws ThingsboardException {
        try {
            HttpIntegrationConfig httpIntegrationConfig = getClientConfiguration(clientConfiguration, HttpIntegrationConfig.class);
            TbHttpClient.buildEncodedUri(httpIntegrationConfig.getRestEndpointUrl());
        } catch (Exception e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void doCheckConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {
        try {
            tbHttpClient = new TbHttpClient(getClientConfiguration(integration, HttpIntegrationConfig.class), ctx, null);
            tbHttpClient.checkConnection();
        } catch (Exception e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        } finally {
            if (tbHttpClient != null) {
                tbHttpClient.destroy();
            }
        }
    }

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);

        config = getClientConfiguration(lifecycleMsg, HttpIntegrationConfig.class);
        tbHttpClient = new TbHttpClient(config, context, metadataTemplate);

        startProcessingIntegrationMessages(this);
    }

    @Override
    protected void doProcess(PublishIntegrationMsgProto msg, BasicCallback callback) {
        tbHttpClient.processMessage(getRequestBody(msg), callback);
    }

    private Object getRequestBody(PublishIntegrationMsgProto msg) {
        ByteString payload = msg.getPublishMsgProto().getPayload();
        if (config.isSendOnlyMsgPayload()) {
            return parsePayload(payload);
        }
        return parsePayloadAddToBody(payload, constructBody(msg));
    }

    private Object parsePayload(ByteString payload) {
        try {
            return switch (config.getPayloadContentType()) {
                case JSON -> JacksonUtil.fromBytes(payload.toByteArray());
                case TEXT -> payload.toStringUtf8();
                case BINARY -> payload.toByteArray();
            };
        } catch (Exception e) {
            if (config.isSendBinaryOnParseFailure()) {
                log.warn("[{}][{}] Failed to parse msg payload to {}: {}", getId(), getName(),
                        config.getPayloadContentType(), payload, e);
                return payload.toByteArray();
            } else {
                throw new RuntimeException("Failed to parse msg payload to " + config.getPayloadContentType() + ": " + payload);
            }
        }
    }

    private ObjectNode parsePayloadAddToBody(ByteString payload, ObjectNode request) {
        try {
            switch (config.getPayloadContentType()) {
                case JSON -> request.set("payload", JacksonUtil.fromBytes(payload.toByteArray()));
                case TEXT -> request.put("payload", payload.toStringUtf8());
                case BINARY -> request.put("payload", payload.toByteArray());
            }
        } catch (Exception e) {
            if (config.isSendBinaryOnParseFailure()) {
                log.warn("[{}][{}] Failed to parse msg payload to {}: {}", getId(), getName(),
                        config.getPayloadContentType(), payload, e);
                request.put("payload", payload.toByteArray());
            } else {
                throw new RuntimeException("Failed to parse msg payload to " + config.getPayloadContentType() + ": " + payload);
            }
        }
        return request;
    }

    @Override
    public void doStopProcessingPersistedMessages() {
        if (tbHttpClient != null) {
            tbHttpClient.destroy();
        }
    }

}
