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
package org.thingsboard.mqtt.broker.exception;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import static org.assertj.core.api.Assertions.assertThat;

class ThingsboardErrorResponseHandlerStoreFullTest {

    private static final String FULL_MESSAGE = "Not enough free disk space for the store at /data: 1 bytes free, watermark 2 bytes";

    private final ThingsboardErrorResponseHandler handler = new ThingsboardErrorResponseHandler();

    @Test
    void givenStoreFullException_whenHandle_thenInsufficientStorage507WithItsMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new StoreFullException(FULL_MESSAGE), response);

        assertThat(response.getStatus()).isEqualTo(507);
        JsonNode body = JacksonUtil.toJsonNode(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(507);
        assertThat(body.get("message").asText()).isEqualTo(FULL_MESSAGE);
    }

    @Test
    void givenAnyOtherStoreException_whenHandle_thenGeneral500AsBefore() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new StoreException("Failed to write to the store"), response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(JacksonUtil.toJsonNode(response.getContentAsString()).get("message").asText()).isEqualTo("Failed to write to the store");
    }

}
