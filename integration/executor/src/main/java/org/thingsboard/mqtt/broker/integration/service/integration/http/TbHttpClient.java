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

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.BasicCredentials;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.CredentialsType;
import org.thingsboard.mqtt.broker.queue.util.IntegrationProtoConverter;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Data
@Slf4j
public class TbHttpClient {

    private static final String STATUS = "status";
    private static final String STATUS_CODE = "statusCode";
    private static final String STATUS_REASON = "statusReason";
    private static final String ERROR = "error";
    private static final String ERROR_BODY = "error_body";

    private static final String MAX_IN_MEMORY_BUFFER_SIZE_IN_KB = "tb.ie.http.maxInMemoryBufferSizeInKb";

    private final HttpIntegrationConfig config;
    private final IntegrationContext ctx;
    private final UplinkMetaData ieMetaData;
    private final WebClient webClient;

    private EventLoopGroup eventLoopGroup;
    private Semaphore semaphore;
    private URI uri;

    TbHttpClient(HttpIntegrationConfig config, IntegrationContext ctx, UplinkMetaData ieMetaData) {
        try {
            this.config = config;
            this.ctx = ctx;
            this.ieMetaData = ieMetaData;
            if (config.getMaxParallelRequestsCount() > 0) {
                semaphore = new Semaphore(config.getMaxParallelRequestsCount());
            }
            uri = buildEncodedUri(config.getRestEndpointUrl());

            ConnectionProvider connectionProvider = ConnectionProvider
                    .builder("ie-http-client")
                    .maxConnections(getPoolMaxConnections())
                    .build();

            HttpClient httpClient = HttpClient.create(connectionProvider)
                    .runOn(getSharedOrCreateEventLoopGroup(ctx.getSharedEventLoop()))
                    .doOnConnected(c ->
                            c.addHandlerLast(new ReadTimeoutHandler(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

            SslContext sslContext = config.getCredentials().initSslContext();
            httpClient = httpClient.secure(t -> t.sslContext(sslContext));

            validateMaxInMemoryBufferSize(config);

            this.webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(
                            (config.getMaxInMemoryBufferSizeInKb() > 0 ? config.getMaxInMemoryBufferSizeInKb() : 256) * 1024))
                    .build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    private int getPoolMaxConnections() {
        String poolMaxConnectionsEnv = System.getenv("TB_IE_HTTP_CLIENT_POOL_MAX_CONNECTIONS");

        int poolMaxConnections;
        if (poolMaxConnectionsEnv != null) {
            poolMaxConnections = Integer.parseInt(poolMaxConnectionsEnv);
        } else {
            poolMaxConnections = ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS;
        }
        return poolMaxConnections;
    }

    private void validateMaxInMemoryBufferSize(HttpIntegrationConfig config) {
        int systemMaxInMemoryBufferSizeInKb = 25000;
        try {
            Properties properties = System.getProperties();
            if (properties.containsKey(MAX_IN_MEMORY_BUFFER_SIZE_IN_KB)) {
                systemMaxInMemoryBufferSizeInKb = Integer.parseInt(properties.getProperty(MAX_IN_MEMORY_BUFFER_SIZE_IN_KB));
            }
        } catch (Exception ignored) {
        }
        if (config.getMaxInMemoryBufferSizeInKb() > systemMaxInMemoryBufferSizeInKb) {
            throw new RuntimeException("The configured maximum in-memory buffer size (in KB) exceeds the system limit for this parameter.\n" +
                    "The system limit is " + systemMaxInMemoryBufferSizeInKb + " KB.\n" +
                    "Please use the system variable '" + MAX_IN_MEMORY_BUFFER_SIZE_IN_KB + "' to override the system limit.");
        }
    }

    EventLoopGroup getSharedOrCreateEventLoopGroup(EventLoopGroup eventLoopGroupShared) {
        return Objects.requireNonNullElseGet(eventLoopGroupShared, () -> this.eventLoopGroup = new NioEventLoopGroup());
    }

    void destroy() {
        if (this.eventLoopGroup != null) {
            this.eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    public void processMessage(PublishIntegrationMsgProto msg, BasicCallback callback) {
        try {
            if (semaphore != null && !semaphore.tryAcquire(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)) {
                log.warn("[{}][{}] Timeout during waiting for reply!", ctx.getLifecycleMsg().getIntegrationId(), ctx.getLifecycleMsg().getName());
                callback.onFailure(new RuntimeException("Timeout during waiting for reply!"));
            }

            HttpMethod method = HttpMethod.valueOf(config.getRequestMethod());
            RequestBodySpec request = webClient
                    .method(method)
                    .uri(uri)
                    .headers(this::prepareHeaders);

            if ((HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) ||
                    HttpMethod.PATCH.equals(method))) {
                request.body(BodyInserters.fromValue(constructBody(msg)));
            }

            processRequest(request, callback);
        } catch (InterruptedException e) {
            log.warn("[{}][{}] Interrupted while trying to acquire the lock", ctx.getLifecycleMsg().getIntegrationId(), ctx.getLifecycleMsg().getName(), e);
            callback.onFailure(e);
        }
    }

    public static URI buildEncodedUri(String endpointUrl) {
        if (endpointUrl == null) {
            throw new RuntimeException("Url string cannot be null!");
        }
        if (endpointUrl.isEmpty()) {
            throw new RuntimeException("Url string cannot be empty!");
        }

        URI uri = UriComponentsBuilder.fromUriString(endpointUrl).build().encode().toUri();
        if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
            throw new RuntimeException("Transport scheme(protocol) must be provided!");
        }

        boolean authorityNotValid = uri.getAuthority() == null || uri.getAuthority().isEmpty();
        boolean hostNotValid = uri.getHost() == null || uri.getHost().isEmpty();
        if (authorityNotValid || hostNotValid) {
            throw new RuntimeException("Url string is invalid!");
        }

        return uri;
    }

    private Object constructBody(PublishIntegrationMsgProto msg) {
        ObjectNode request = JacksonUtil.newObjectNode();

        PublishMsgProto publishMsgProto = msg.getPublishMsgProto();
        try {
            switch (config.getPayloadContentType()) {
                case JSON -> request.set("payload", JacksonUtil.fromBytes(publishMsgProto.getPayload().toByteArray()));
                case TEXT -> request.put("payload", publishMsgProto.getPayload().toStringUtf8());
                case BINARY -> request.put("payload", publishMsgProto.getPayload().toByteArray());
            }
        } catch (Exception e) {
            if (config.isSendBinaryOnParseFailure()) {
                log.warn("Failed to parse msg payload to {}: {}", config.getPayloadContentType(), msg, e);
                request.put("payload", publishMsgProto.getPayload().toByteArray());
            } else {
                throw new RuntimeException("Failed to parse msg payload to " + config.getPayloadContentType());
            }
        }

        request.put("topicName", publishMsgProto.getTopicName());
        request.put("clientId", publishMsgProto.getClientId());
        request.put("eventType", "PUBLISH_MSG");
        request.put("qos", publishMsgProto.getQos());
        request.put("retain", publishMsgProto.getRetain());
        request.put("tbmqIeNode", ctx.getServiceId());
        request.put("tbmqNode", msg.getTbmqNode());
        request.put("ts", msg.getTimestamp());
        request.set("props", IntegrationProtoConverter.fromProto(publishMsgProto.getUserPropertiesList()));
        request.set("metadata", JacksonUtil.valueToTree(ieMetaData.getKvMap()));

        return request;
    }

    private void processResponse(ResponseEntity<String> response) {
        ObjectNode result = JacksonUtil.newObjectNode();
        ObjectNode metadata = JacksonUtil.newObjectNode();
        result.set("metadata", metadata);

        HttpStatus httpStatus = (HttpStatus) response.getStatusCode();
        result.put(STATUS, httpStatus.name());
        result.put(STATUS_CODE, response.getStatusCode().value() + "");
        result.put(STATUS_REASON, httpStatus.getReasonPhrase());
        headersToMetaData(response.getHeaders(), metadata::put);
        log.debug("processResponse {}", result);
    }

    private ObjectNode processFailureResponse(ResponseEntity<String> response) {
        ObjectNode result = JacksonUtil.newObjectNode();
        ObjectNode metadata = JacksonUtil.newObjectNode();
        result.set("metadata", metadata);

        HttpStatus httpStatus = (HttpStatus) response.getStatusCode();
        result.put(STATUS, httpStatus.name());
        result.put(STATUS_CODE, httpStatus.value() + "");
        result.put(STATUS_REASON, httpStatus.getReasonPhrase());
        result.put(ERROR_BODY, response.getBody());
        headersToMetaData(response.getHeaders(), metadata::put);
        log.debug("processFailureResponse {}", result);
        return result;
    }

    private void processException(Throwable e) {
        ObjectNode result = JacksonUtil.newObjectNode();
        result.put(ERROR, e.getClass() + ": " + e.getMessage());
        if (e instanceof WebClientResponseException restClientResponseException) {
            result.put(STATUS, restClientResponseException.getStatusText());
            result.put(STATUS_CODE, restClientResponseException.getStatusCode().value() + "");
            result.put(ERROR_BODY, restClientResponseException.getResponseBodyAsString());
        }
        log.warn("processException {}", result, e);
    }

    void headersToMetaData(Map<String, List<String>> headers, BiConsumer<String, String> consumer) {
        if (headers == null) {
            return;
        }
        headers.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                if (values.size() == 1) {
                    consumer.accept(key, values.get(0));
                } else {
                    consumer.accept(key, JacksonUtil.toString(values));
                }
            }
        });
    }

    private void prepareHeaders(HttpHeaders headers) {
        config.getHeaders().forEach(headers::add);
        ClientCredentials credentials = config.getCredentials();
        if (CredentialsType.BASIC == credentials.getType()) {
            BasicCredentials basicCredentials = (BasicCredentials) credentials;
            String authString = basicCredentials.getUsername() + ":" + basicCredentials.getPassword();
            String encodedAuthString = new String(Base64.encodeBase64(authString.getBytes(StandardCharsets.UTF_8)));
            headers.add("Authorization", "Basic " + encodedAuthString);
        }
    }

    public void checkConnection() {
        try {
            if (semaphore != null && !semaphore.tryAcquire(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout during waiting for reply on check connection!");
            }

            RequestBodySpec request = webClient
                    .method(HttpMethod.HEAD)
                    .uri(uri)
                    .headers(this::prepareHeaders);

            processRequest(request, ctx.getCallback());
        } catch (InterruptedException e) {
            log.warn("[{}][{}] Interrupted while trying to acquire the lock on check connection", ctx.getLifecycleMsg().getIntegrationId(), ctx.getLifecycleMsg().getName(), e);
            ctx.getCallback().onFailure(e);
        }
    }

    private void processRequest(RequestBodySpec request, BasicCallback callback) {
        request
                .retrieve()
                .toEntity(String.class)
                .subscribe(responseEntity -> {
                    if (semaphore != null) {
                        semaphore.release();
                    }

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        processResponse(responseEntity);
                        callback.onSuccess();
                    } else {
                        ObjectNode result = processFailureResponse(responseEntity);
                        callback.onFailure(new RuntimeException(JacksonUtil.toString(result)));
                    }
                }, throwable -> {
                    if (semaphore != null) {
                        semaphore.release();
                    }

                    processException(throwable);
                    callback.onFailure(throwable);
                });
    }

}
