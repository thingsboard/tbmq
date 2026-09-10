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
package org.thingsboard.mqtt.broker.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link RestPublishRequestSizeInterceptor#PAYLOAD_ENCODING_FACTOR} against a real wire body rather than
 * against itself: the accepted-case body is built by actually escaping a max-size payload.
 */
class RestPublishRequestSizeInterceptorTest {

    private static final long MAX_PAYLOAD_SIZE = 65536;

    private final RestPublishRequestSizeInterceptor interceptor = new RestPublishRequestSizeInterceptor(MAX_PAYLOAD_SIZE);

    @Test
    void givenMaxSizeAsciiPayloadEscapedAsUnicode_whenPreHandle_thenAccepted() throws Exception {
        // the worst legal string encoding: every ASCII byte of the payload written as \\uXXXX (6 bytes each)
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < MAX_PAYLOAD_SIZE; i++) {
            escaped.append(String.format("\\u%04x", 'a' + (i % 26)));
        }
        byte[] body = ("{\"topic\":\"sensors/1\",\"payloadEncoding\":\"TEXT\",\"payload\":\"" + escaped + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(requestWithBody(body), response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void givenBodyOverTheBound_whenPreHandle_thenRefusedWith413() throws Exception {
        long bound = MAX_PAYLOAD_SIZE * RestPublishRequestSizeInterceptor.PAYLOAD_ENCODING_FACTOR
                + RestPublishRequestSizeInterceptor.ENVELOPE_ALLOWANCE_BYTES;
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(requestWithBody(new byte[(int) bound + 1]), response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(response.getContentAsString()).contains("max_payload_size is " + MAX_PAYLOAD_SIZE);
    }

    @Test
    void givenNoContentLength_whenPreHandle_thenRefusedWith411() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.LENGTH_REQUIRED.value());
    }

    private static MockHttpServletRequest requestWithBody(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mqtt/publish");
        request.setContent(body);
        return request;
    }

}
