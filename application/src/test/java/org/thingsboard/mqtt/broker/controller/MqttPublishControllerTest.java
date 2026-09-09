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

import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.mqtt.publish.ExternalPublishRateLimitException;
import org.thingsboard.mqtt.broker.service.mqtt.publish.ExternalPublishService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MqttPublishControllerTest extends AbstractControllerTest {

    @MockBean
    private ExternalPublishService externalPublishService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void testPublishAccepted() throws Exception {
        doAnswer(invocation -> {
            TbQueueCallback callback = invocation.getArgument(1);
            callback.onSuccess(null);
            return null;
        }).when(externalPublishService).publish(any(), any());

        doPostAsync("/api/mqtt/publish", validRequest(), -1L)
                .andExpect(status().isAccepted());
    }

    @Test
    public void testPublishRateLimited() throws Exception {
        doAnswer(invocation -> {
            TbQueueCallback callback = invocation.getArgument(1);
            callback.onFailure(new ExternalPublishRateLimitException());
            return null;
        }).when(externalPublishService).publish(any(), any());

        doPostAsync("/api/mqtt/publish", validRequest(), -1L)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    public void testPublishQueueFailure() throws Exception {
        doAnswer(invocation -> {
            TbQueueCallback callback = invocation.getArgument(1);
            callback.onFailure(new RuntimeException("queue unavailable"));
            return null;
        }).when(externalPublishService).publish(any(), any());

        doPostAsync("/api/mqtt/publish", validRequest(), -1L)
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    public void testPublishRequiresAuthentication() throws Exception {
        logout();

        doPost("/api/mqtt/publish", validRequest())
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testPublishRejectsInvalidQos() throws Exception {
        RestPublishRequest request = validRequest();
        request.setQos(3);

        doPost("/api/mqtt/publish", request)
                .andExpect(status().isBadRequest());
    }

    private RestPublishRequest validRequest() {
        RestPublishRequest request = new RestPublishRequest();
        request.setTopic("devices/a/commands");
        request.setPayload(new byte[]{1, 2, 3});
        request.setQos(1);
        return request;
    }

}
