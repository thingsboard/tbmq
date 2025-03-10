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
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;
import org.thingsboard.mqtt.broker.integration.api.util.ExceptionUtil;
import org.thingsboard.mqtt.broker.queue.util.IntegrationProtoConverter;

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
        log.info("[{}][{}] Init integration", params.getLifecycleMsg().getIntegrationId(), params.getLifecycleMsg().getName());
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

    @Override
    public void destroy() {
        if (this.lifecycleMsg != null) {
            log.info("[{}][{}] Destroy integration", this.lifecycleMsg.getIntegrationId(), this.lifecycleMsg.getName());
        }
        stopProcessingPersistedMessages();
    }

    @Override
    public void destroyAndClearData() {
        if (this.lifecycleMsg != null) {
            log.info("[{}][{}] Destroy and clear integration", this.lifecycleMsg.getIntegrationId(), this.lifecycleMsg.getName());
        }
        stopProcessingPersistedMessages();
        clearIntegrationMessages();
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        if (this.lifecycleMsg != null) {
            log.info("[{}][{}] Update integration", this.lifecycleMsg.getIntegrationId(), this.lifecycleMsg.getName());
        }
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

    protected void startProcessingIntegrationMessages(TbPlatformIntegration integration) {
        context.startProcessingIntegrationMessages(integration);
    }

    protected void stopProcessingPersistedMessages() {
        doStopClient();

        if (lifecycleMsg == null) {
            log.debug("[{}] Integration was not initialized properly. Skip stopProcessingPersistedMessages", this.getClass());
            return;
        }
        context.stopProcessingPersistedMessages(lifecycleMsg.getIntegrationId().toString());
    }

    protected void clearIntegrationMessages() {
        if (lifecycleMsg == null) {
            log.debug("[{}] Integration was not initialized properly. Skip clearIntegrationMessages", this.getClass());
            return;
        }
        context.clearIntegrationMessages(lifecycleMsg.getIntegrationId().toString());
    }

    protected String constructValue(PublishIntegrationMsgProto msg) {
        return JacksonUtil.toString(constructBody(msg));
    }

    protected ObjectNode constructBody(PublishIntegrationMsgProto msg) {
        PublishMsgProto publishMsgProto = msg.getPublishMsgProto();

        ObjectNode request = JacksonUtil.newObjectNode();
        request.put("payload", publishMsgProto.getPayload().toByteArray());
        request.put("topicName", publishMsgProto.getTopicName());
        request.put("clientId", publishMsgProto.getClientId());
        request.put("eventType", "PUBLISH_MSG");
        request.put("qos", publishMsgProto.getQos());
        request.put("retain", publishMsgProto.getRetain());
        request.put("tbmqIeNode", context.getServiceId());
        request.put("tbmqNode", msg.getTbmqNode());
        request.put("ts", msg.getTimestamp());
        request.set("props", IntegrationProtoConverter.fromProto(publishMsgProto.getUserPropertiesList()));
        request.set("metadata", JacksonUtil.valueToTree(metadataTemplate.getKvMap()));

        return request;
    }

    protected void handleMsgProcessingFailure(Throwable throwable) {
        integrationStatistics.incErrorsOccurred();
        context.saveErrorEvent(getErrorEvent(throwable));
    }

    private ErrorEvent getErrorEvent(Throwable throwable) {
        return ErrorEvent
                .builder()
                .entityId(lifecycleMsg.getIntegrationId())
                .serviceId(context.getServiceId())
                .method("onMsgProcess")
                .error(getError(throwable))
                .build();
    }

    private String getError(Throwable throwable) {
        return throwable == null ? "Unspecified server error" : getRealErrorMsg(throwable);
    }

    private String getRealErrorMsg(Throwable throwable) {
        if (StringUtils.isNotEmpty(throwable.getMessage())) {
            return throwable.getMessage();
        }
        if (StringUtils.isNotEmpty(throwable.getCause().getMessage())) {
            return throwable.getCause().getMessage();
        }
        return throwable.getCause().toString();
    }

    protected void handleLifecycleEvent(ComponentLifecycleEvent event) {
        handleLifecycleEvent(event, null);
    }

    protected void handleLifecycleEvent(ComponentLifecycleEvent event, Exception e) {
        context.saveLifecycleEvent(event, e);
    }

    protected ContentType getDefaultUplinkContentType() {
        return ContentType.JSON;
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

    protected void doStopClient() {

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

    protected UUID getId() {
        return lifecycleMsg.getIntegrationId();
    }

    protected String getName() {
        return lifecycleMsg.getName();
    }

}
