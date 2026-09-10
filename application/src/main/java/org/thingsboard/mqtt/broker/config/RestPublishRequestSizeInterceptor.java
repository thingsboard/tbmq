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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.controller.MqttPublishController;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponse;

import java.io.IOException;

/**
 * Refuses oversized REST publish requests before the body is read. The decoded-payload check in
 * {@link org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishServiceImpl} runs only after Jackson has
 * materialised the whole JSON body, so without this guard {@code server.rest_publish.max_payload_size} would bound
 * what gets published but not what gets allocated.
 */
@Slf4j
@Component
public class RestPublishRequestSizeInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    /**
     * Room for the JSON envelope around the payload: topic, flags and the MQTT 5 properties block.
     */
    public static final long ENVELOPE_ALLOWANCE_BYTES = 16 * 1024;

    /**
     * Worst-case growth of a string payload on the wire. Base64 adds 4/3; JSON escaping is worse: a serializer that
     * writes non-ASCII as {@code \\uXXXX} (Python's json.dumps default) turns a 2-byte UTF-8 character into 6 bytes
     * (x3), and one that escapes ASCII too turns 1 byte into 6 (x6). 6 is the ceiling for any escaped string.
     * <p>
     * This is a coarse pre-read bound, not the limit itself: it can still refuse a legal request whose size comes
     * from something other than the payload — a pretty-printed {@code JSON} payload (published compact, so the
     * whitespace is unbounded on the wire) or a properties block larger than {@link #ENVELOPE_ALLOWANCE_BYTES}. The
     * decoded-payload check in the service is authoritative.
     */
    public static final long PAYLOAD_ENCODING_FACTOR = 6;

    private final long maxPayloadSize;
    private final long maxRequestBytes;

    public RestPublishRequestSizeInterceptor(@Value("${server.rest_publish.max_payload_size:65536}") long maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
        this.maxRequestBytes = maxPayloadSize * PAYLOAD_ENCODING_FACTOR + ENVELOPE_ALLOWANCE_BYTES;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(MqttPublishController.PUBLISH_PATH);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            return reject(response, HttpStatus.LENGTH_REQUIRED, "Content-Length header is required");
        }
        if (contentLength > maxRequestBytes) {
            log.debug("Refusing REST publish request of {} bytes, limit is {} bytes", contentLength, maxRequestBytes);
            return reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body of " + contentLength + " bytes exceeds the maximum of "
                    + maxRequestBytes + " bytes (max_payload_size is " + maxPayloadSize + " bytes)");
        }
        return true;
    }

    private static boolean reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        JacksonUtil.writeValue(response.getWriter(), ThingsboardErrorResponse.of(message, ThingsboardErrorCode.BAD_REQUEST_PARAMS, status));
        return false;
    }

}
