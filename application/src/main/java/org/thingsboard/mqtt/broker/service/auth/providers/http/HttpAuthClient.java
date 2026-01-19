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
package org.thingsboard.mqtt.broker.service.auth.providers.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
import org.thingsboard.mqtt.broker.common.data.credentials.BasicCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.CredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpAuthCallback;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.util.UrlUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Data
@Slf4j
public class HttpAuthClient {

    private static final String STATUS = "status";
    private static final String STATUS_CODE = "statusCode";
    private static final String STATUS_REASON = "statusReason";
    private static final String ERROR = "error";
    private static final String CAUSE = "cause";
    private static final String ERROR_BODY = "error_body";

    private static final String MAX_IN_MEMORY_BUFFER_SIZE_IN_KB = "tb.http.auth.maxInMemoryBufferSizeInKb";

    private final HttpMqttAuthProviderConfiguration config;
    private final WebClient webClient;

    private EventLoopGroup eventLoopGroup;
    private Semaphore semaphore;
    private URI uri;

    HttpAuthClient(HttpMqttAuthProviderConfiguration configuration) {
        try {
            config = configuration;
            if (config.getMaxParallelRequestsCount() > 0) {
                semaphore = new Semaphore(config.getMaxParallelRequestsCount());
            }
            uri = UrlUtils.buildEncodedUri(config.getRestEndpointUrl());

            ConnectionProvider connectionProvider = ConnectionProvider
                    .builder("http-auth-provider-client")
                    .maxConnections(getPoolMaxConnections())
                    .build();

            eventLoopGroup = new NioEventLoopGroup();
            HttpClient httpClient = HttpClient.create(connectionProvider)
                    .runOn(eventLoopGroup)
                    .doOnConnected(c ->
                            c.addHandlerLast(new ReadTimeoutHandler(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

            SslContext sslContext = config.getCredentials().initSslContext();
            httpClient = httpClient.secure(t -> t.sslContext(sslContext));

            validateMaxInMemoryBufferSize(config);

            webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(
                            (config.getMaxInMemoryBufferSizeInKb() > 0 ? config.getMaxInMemoryBufferSizeInKb() : 256) * 1024))
                    .build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    void destroy() {
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    private int getPoolMaxConnections() {
        String poolMaxConnectionsEnv = System.getenv("TB_HTTP_AUTH_CLIENT_POOL_MAX_CONNECTIONS");

        int poolMaxConnections;
        if (poolMaxConnectionsEnv != null) {
            poolMaxConnections = Integer.parseInt(poolMaxConnectionsEnv);
        } else {
            poolMaxConnections = ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS;
        }
        return poolMaxConnections;
    }

    private void validateMaxInMemoryBufferSize(HttpMqttAuthProviderConfiguration config) {
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

    public void processMessage(String requestBody, HttpAuthCallback callback) {
        try {
            if (semaphore != null && !semaphore.tryAcquire(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)) {
                log.warn("HTTP auth provider timeout during waiting for reply!");
                callback.onFailure(new RuntimeException("Timeout during waiting for reply!"));
            }

            HttpMethod method = HttpMethod.valueOf(config.getRequestMethod());
            URI finalUri = HttpMethod.GET.equals(method) ? buildUriWithQueryParams(requestBody) : uri;

            RequestBodySpec request = webClient
                    .method(method)
                    .uri(finalUri)
                    .headers(this::prepareHeaders);

            if ((HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) ||
                    HttpMethod.PATCH.equals(method))) {
                request.body(BodyInserters.fromValue(requestBody));
            }

            processRequest(request, callback);
        } catch (InterruptedException e) {
            log.warn("HTTP auth provider client interrupted while trying to acquire the lock", e);
            callback.onFailure(e);
        }
    }

    private URI buildUriWithQueryParams(String jsonBody) {
        try {
            Map<String, Object> params = JacksonUtil.convertValue(jsonBody, new TypeReference<>() {
            });
            if (params == null) {
                return uri;
            }

            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri);
            params.forEach((key, value) -> {
                if (value != null) {
                    builder.queryParam(key, value.toString());
                }
            });
            return builder.build().toUri();
        } catch (Exception e) {
            log.warn("Failed to parse request body as JSON for GET query parameters: {}", jsonBody);
            return uri;
        }
    }

    private void processResponse(ResponseEntity<JsonNode> response) {
        if (log.isDebugEnabled()) {
            ObjectNode result = JacksonUtil.newObjectNode();
            ObjectNode metadata = JacksonUtil.newObjectNode();
            result.set("metadata", metadata);

            HttpStatus httpStatus = (HttpStatus) response.getStatusCode();
            result.put(STATUS, httpStatus.name());
            result.put(STATUS_CODE, response.getStatusCode().value() + "");
            result.put(STATUS_REASON, httpStatus.getReasonPhrase());
            headersToMetaData(response.getHeaders(), metadata::put);
            log.debug("HTTP auth provider processResponse {}", result);
        }
    }

    private ObjectNode processFailureResponse(ResponseEntity<JsonNode> response) {
        ObjectNode result = JacksonUtil.newObjectNode();
        ObjectNode metadata = JacksonUtil.newObjectNode();
        result.set("metadata", metadata);

        HttpStatus httpStatus = (HttpStatus) response.getStatusCode();
        result.put(STATUS, httpStatus.name());
        result.put(STATUS_CODE, httpStatus.value() + "");
        result.put(STATUS_REASON, httpStatus.getReasonPhrase());
        result.set(ERROR_BODY, response.getBody());
        headersToMetaData(response.getHeaders(), metadata::put);
        log.warn("HTTP auth provider processFailureResponse {}", result);
        return result;
    }

    private void processException(Throwable e) {
        ObjectNode result = JacksonUtil.newObjectNode();
        result.put(ERROR, e.getClass() + ": " + e.getMessage());
        if (e instanceof WebClientResponseException restClientResponseException) {
            result.put(STATUS, restClientResponseException.getStatusText());
            result.put(STATUS_CODE, restClientResponseException.getStatusCode().value() + "");
            result.put(ERROR_BODY, restClientResponseException.getResponseBodyAsString());
        } else if (e.getCause() instanceof ReadTimeoutException) {
            result.put(CAUSE, "ReadTimeoutException");
            log.warn("HTTP auth provider processException {}", result);
            return;
        }
        log.warn("HTTP auth provider processException {}", result, e);
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
            String encodedAuthString = new String(Base64.getEncoder().encode(authString.getBytes(StandardCharsets.UTF_8)));
            headers.add("Authorization", "Basic " + encodedAuthString);
        }
    }

    public void checkConnection(HttpAuthCallback callback) {
        try {
            if (semaphore != null && !semaphore.tryAcquire(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout during waiting for reply on check connection!");
            }

            RequestBodySpec request = webClient
                    .method(HttpMethod.HEAD)
                    .uri(uri)
                    .headers(this::prepareHeaders);

            processRequest(request, callback);
        } catch (InterruptedException e) {
            log.warn("HTTP auth provider client interrupted while trying to acquire the lock on check connection", e);
            callback.onFailure(e);
        }
    }

    private void processRequest(RequestBodySpec request, HttpAuthCallback callback) {
        request
                .retrieve()
                .toEntity(JsonNode.class)
                .subscribe(responseEntity -> {
                    if (semaphore != null) {
                        semaphore.release();
                    }

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        processResponse(responseEntity);
                        callback.onSuccess(responseEntity);
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
