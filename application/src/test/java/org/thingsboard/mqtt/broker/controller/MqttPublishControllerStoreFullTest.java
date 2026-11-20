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

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.exception.PublishLimitExceededException;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponse;
import org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqttPublishControllerStoreFullTest {

    private static final String FULL_MESSAGE = "Not enough free disk space for the store at /data: 1 bytes free, watermark 2 bytes";

    private final RestPublishService restPublishService = mock(RestPublishService.class);
    private final MqttPublishController controller = new MqttPublishController(restPublishService);

    @Test
    void givenStoreFull_whenPublish_thenInsufficientStorage507WithItsMessage() {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFailedFuture(new StoreFullException(FULL_MESSAGE)));

        ResponseEntity<?> response = publish();

        assertThat(response.getStatusCode().value()).isEqualTo(507);
        ThingsboardErrorResponse body = (ThingsboardErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(507);
        assertThat(body.getMessage()).isEqualTo(FULL_MESSAGE);
    }

    @Test
    void givenPublishLimitExceeded_whenPublish_thenStillTooManyRequests() {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFailedFuture(new PublishLimitExceededException("pipeline is full")));

        ResponseEntity<?> response = publish();

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(((ThingsboardErrorResponse) response.getBody()).getMessage()).isEqualTo("Failed to publish the message: pipeline is full");
    }

    private ResponseEntity<?> publish() {
        RestPublishRequest request = new RestPublishRequest();
        request.setTopic("devices/a/commands");
        request.setPayload(new TextNode("hello"));
        request.setQos(1);
        return (ResponseEntity<?>) controller.publish(request).getResult();
    }

}
