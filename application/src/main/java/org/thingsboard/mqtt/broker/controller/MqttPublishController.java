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
package org.thingsboard.mqtt.broker.controller;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.config.annotations.ApiOperation;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.dto.RestPublishResponse;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponse;
import org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishService;

@Slf4j
@RestController
@RequestMapping("/api/mqtt")
@RequiredArgsConstructor
public class MqttPublishController extends BaseController {

    public static final String PUBLISH_PATH = "/api/mqtt/publish";

    private final RestPublishService restPublishService;

    @Value("${server.rest_publish.timeout_ms:30000}")
    private long timeoutMs;

    @ApiOperation(value = "Publish MQTT message (publishMqttMessage)",
            notes = "Publishes a message to all MQTT clients whose subscriptions match the topic, through the same pipeline as a " +
                    "PUBLISH received from a client: total throughput quota, retained-message store, publish queue and delivery. " +
                    "The publisher client id is '" + BrokerConstants.REST_API_CLIENT_ID + "'. " +
                    "A 2xx response means the broker queue accepted the message, not that any client received it. " +
                    "Payloads are UTF-8 text by default; set 'payloadEncoding' to BASE64 for binary data.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Accepted by the queue; at least one subscription matched the topic.",
                            content = @Content(schema = @Schema(implementation = RestPublishResponse.class))),
                    @ApiResponse(responseCode = "202", description = "Accepted by the queue; no subscription matched the topic (reason code 16). " +
                            "A retained message is still stored.",
                            content = @Content(schema = @Schema(implementation = RestPublishResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid topic, payload, encoding or MQTT property.",
                            content = @Content(schema = @Schema(implementation = ThingsboardErrorResponse.class))),
                    @ApiResponse(responseCode = "413", description = "Request body exceeds the limit derived from 'server.rest_publish.max_payload_size'.",
                            content = @Content(schema = @Schema(implementation = ThingsboardErrorResponse.class))),
                    @ApiResponse(responseCode = "429", description = "Refused by the total incoming throughput quota.",
                            content = @Content(schema = @Schema(implementation = ThingsboardErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "The broker queue rejected the message or did not acknowledge it in time.",
                            content = @Content(schema = @Schema(implementation = ThingsboardErrorResponse.class)))
            })
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping("/publish")
    public DeferredResult<ResponseEntity<?>> publish(@Valid @RequestBody RestPublishRequest request) {
        DeferredResult<ResponseEntity<?>> result = new DeferredResult<>(timeoutMs);
        result.onTimeout(() -> {
            log.warn("[{}] REST publish timed out after {} ms waiting for the queue acknowledgement", request.getTopic(), timeoutMs);
            result.setResult(serviceUnavailable("Publish timed out waiting for the broker queue"));
        });

        ListenableFuture<RestPublishResponse> future = restPublishService.publish(request);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(RestPublishResponse response) {
                HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.ACCEPTED;
                result.setResult(ResponseEntity.status(status).body(response));
            }

            @Override
            public void onFailure(Throwable t) {
                result.setResult(serviceUnavailable("Failed to publish the message: " + t.getMessage()));
            }
        }, MoreExecutors.directExecutor());
        return result;
    }

    private static ResponseEntity<?> serviceUnavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ThingsboardErrorResponse.of(message, ThingsboardErrorCode.GENERAL, HttpStatus.SERVICE_UNAVAILABLE));
    }

}
