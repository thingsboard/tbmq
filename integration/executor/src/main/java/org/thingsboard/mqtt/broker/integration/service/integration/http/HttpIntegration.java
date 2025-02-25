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
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;

@Slf4j
public class HttpIntegration extends AbstractHttpIntegration {

    private TbHttpClient tbHttpClient;

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

        var httpIntegrationConfig = getClientConfiguration(lifecycleMsg, HttpIntegrationConfig.class);
        tbHttpClient = new TbHttpClient(httpIntegrationConfig, context, metadataTemplate);

        startProcessingIntegrationMessages();
    }

    @Override
    protected void doProcess(PublishIntegrationMsgProto msg, BasicCallback callback) {
        tbHttpClient.processMessage(msg, callback);
    }

    @Override
    public void destroy() {
        stopProcessingPersistedMessages();
    }

    @Override
    public void destroyAndClearData() {
        stopProcessingPersistedMessages();
        clearIntegrationMessages();
    }

    private void startProcessingIntegrationMessages() {
        context.startProcessingIntegrationMessages(this);
    }

    private void stopProcessingPersistedMessages() {
        if (tbHttpClient != null) {
            tbHttpClient.destroy();
        }
        if (lifecycleMsg == null) {
            log.debug("Integration was not initialized properly. Skip stopProcessingPersistedMessages");
            return;
        }
        context.stopProcessingPersistedMessages(lifecycleMsg.getIntegrationId().toString());
    }

    private void clearIntegrationMessages() {
        if (lifecycleMsg == null) {
            log.debug("Integration was not initialized properly. Skip clearIntegrationMessages");
            return;
        }
        context.clearIntegrationMessages(lifecycleMsg.getIntegrationId().toString());
    }
}
