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
package org.thingsboard.mqtt.broker.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;
import org.thingsboard.mqtt.broker.integration.api.util.ExceptionUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

@Slf4j
public abstract class AbstractIntegration implements TbPlatformIntegration {

    protected IntegrationLifecycleMsg lifecycleMsg;
    protected IntegrationContext context;
    protected UplinkMetaData metadataTemplate;
    protected IntegrationStatistics integrationStatistics;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        this.lifecycleMsg = params.getLifecycleMsg();
        this.context = params.getContext();
        Map<String, String> mdMap = new HashMap<>();
        mdMap.put("integrationName", lifecycleMsg.getName());
        JsonNode metadata = lifecycleMsg.getConfiguration().get("metadata");
        if (metadata != null && !metadata.isNull()) {
            for (Iterator<Entry<String, JsonNode>> it = metadata.fields(); it.hasNext(); ) {
                Entry<String, JsonNode> md = it.next();
                mdMap.put(md.getKey(), md.getValue().asText());
            }
        }
        this.metadataTemplate = new UplinkMetaData(getDefaultUplinkContentType(), mdMap);

        if (integrationStatistics == null) {
            this.integrationStatistics = new IntegrationStatistics(context);
        }
    }

    protected ContentType getDefaultUplinkContentType() {
        return ContentType.JSON;
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        destroy();
        init(params);
    }

    @Override
    public IntegrationLifecycleMsg getLifecycleMsg() {
        return lifecycleMsg;
    }

    @Override
    public void validateConfiguration(IntegrationLifecycleMsg lifecycleMsg, boolean allowLocalNetworkHosts) throws ThingsboardException {
        if (lifecycleMsg == null || lifecycleMsg.getConfiguration() == null) {
            throw new IllegalArgumentException("Integration configuration is empty!");
        }
        doValidateConfiguration(lifecycleMsg.getConfiguration().get("clientConfiguration"), allowLocalNetworkHosts);
    }

    @Override
    public void checkConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {
        if (integration == null || integration.getConfiguration() == null) {
            throw new IllegalArgumentException("Integration configuration is empty!");
        }
        doCheckConnection(integration, ctx);
    }

    @Override
    public IntegrationStatistics popStatistics() {
        IntegrationStatistics statistics = this.integrationStatistics;
        this.integrationStatistics = new IntegrationStatistics(context);
        return statistics;
    }

    @Override
    public String getIntegrationId() {
        return getIntegrationUuid().toString();
    }

    @Override
    public UUID getIntegrationUuid() {
        return lifecycleMsg.getIntegrationId();
    }

    protected <T> T getClientConfiguration(Integration configuration, Class<T> clazz) {
        JsonNode clientConfiguration = configuration.getConfiguration().get("clientConfiguration");
        return getClientConfiguration(clientConfiguration, clazz);
    }

    protected <T> T getClientConfiguration(IntegrationLifecycleMsg lifecycleMsg, Class<T> clazz) {
        JsonNode clientConfiguration = lifecycleMsg.getConfiguration().get("clientConfiguration");
        return getClientConfiguration(clientConfiguration, clazz);
    }

    protected <T> T getClientConfiguration(JsonNode clientConfiguration, Class<T> clazz) {
        if (clientConfiguration == null) {
            throw new IllegalArgumentException("clientConfiguration field is missing!");
        } else {
            return JacksonUtil.convertValue(clientConfiguration, clazz);
        }
    }

    protected void doValidateConfiguration(JsonNode clientConfiguration, boolean allowLocalNetworkHosts) throws ThingsboardException {

    }

    protected void doCheckConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {

    }

    protected static boolean isLocalNetworkHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                    address.isSiteLocalAddress()) {
                return true;
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve provided hostname: " + host);
        }
        return false;
    }

    protected String toString(Throwable e) {
        return ExceptionUtil.toString(e, lifecycleMsg.getIntegrationId(), context.isExceptionStackTraceEnabled());
    }

}
