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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.config.annotations.ApiOperation;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.mqtt.publish.ExternalPublishCommand;
import org.thingsboard.mqtt.broker.service.mqtt.publish.ExternalPublishRateLimitException;
import org.thingsboard.mqtt.broker.service.mqtt.publish.ExternalPublishService;

@RestController
@RequestMapping("/api/mqtt")
@RequiredArgsConstructor
public class MqttPublishController extends BaseController {

    private final ExternalPublishService externalPublishService;

    @ApiOperation(value = "Publish MQTT message", notes = "Publishes a message to all matching MQTT subscriptions. " +
            "A successful response means the message was accepted by the broker queue, not that a client received it.")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping("/publish")
    public DeferredResult<ResponseEntity<Void>> publish(@Valid @RequestBody RestPublishRequest request) {
        DeferredResult<ResponseEntity<Void>> result = new DeferredResult<>();
        ExternalPublishCommand command = new ExternalPublishCommand(request.getTopic(), request.getPayload(),
                request.getQos(), request.isRetained(), request.getMessageExpiryInterval(), request.getContentType());
        externalPublishService.publish(command, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                result.setResult(ResponseEntity.accepted().build());
            }

            @Override
            public void onFailure(Throwable t) {
                HttpStatus status = t instanceof ExternalPublishRateLimitException
                        ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
                result.setResult(ResponseEntity.status(status).build());
            }
        });
        return result;
    }

}
