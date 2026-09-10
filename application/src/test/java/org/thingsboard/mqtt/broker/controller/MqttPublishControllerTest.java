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
import com.google.common.util.concurrent.SettableFuture;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.thingsboard.mqtt.broker.config.RestPublishRequestSizeInterceptor;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dto.PayloadEncoding;
import org.thingsboard.mqtt.broker.dto.RestPublishProperties;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.dto.RestPublishResponse;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.TbRateLimitsException;
import org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
@TestPropertySource(properties = {
        "server.rest_publish.max_payload_size=" + MqttPublishControllerTest.MAX_PAYLOAD_SIZE,
        "server.rest_publish.timeout_ms=500"
})
public class MqttPublishControllerTest extends AbstractControllerTest {

    private static final String PUBLISH_URL = MqttPublishController.PUBLISH_PATH;
    static final long MAX_PAYLOAD_SIZE = 1024;
    private static final long MAX_REQUEST_BYTES = MAX_PAYLOAD_SIZE * RestPublishRequestSizeInterceptor.PAYLOAD_ENCODING_FACTOR
            + RestPublishRequestSizeInterceptor.ENVELOPE_ALLOWANCE_BYTES;

    @MockitoBean
    private RestPublishService restPublishService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void givenMatchingSubscribers_whenPublish_thenOkWithSuccessReasonCode() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFuture(RestPublishResponse.success()));

        doPostAsync(PUBLISH_URL, validRequest(), -1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value(0))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    public void givenNoMatchingSubscribers_whenPublish_thenAcceptedWithReasonCode16() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFuture(RestPublishResponse.noMatchingSubscribers()));

        doPostAsync(PUBLISH_URL, validRequest(), -1L)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reasonCode").value(16))
                .andExpect(jsonPath("$.message").value("No matching subscribers"));
    }

    @Test
    public void givenAllFields_whenPublish_thenRequestReachesServiceIntact() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFuture(RestPublishResponse.success()));
        RestPublishRequest request = validRequest();
        request.setPayloadEncoding(PayloadEncoding.BASE64);
        request.setPayload(new TextNode("AQID"));
        request.setQos(2);
        request.setRetain(true);
        RestPublishProperties properties = new RestPublishProperties();
        properties.setPayloadFormatIndicator(1);
        properties.setMessageExpiryInterval(60);
        properties.setContentType("application/octet-stream");
        properties.setResponseTopic("devices/a/replies");
        properties.setCorrelationData("cmVx");
        properties.setUserProperties(Map.of("k", "v"));
        request.setProperties(properties);

        doPostAsync(PUBLISH_URL, request, -1L).andExpect(status().isOk());

        ArgumentCaptor<RestPublishRequest> captor = ArgumentCaptor.forClass(RestPublishRequest.class);
        verify(restPublishService).publish(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(request);
    }

    @Test
    public void givenQuotaExceeded_whenPublish_thenTooManyRequestsWithErrorBody() throws Exception {
        when(restPublishService.publish(any())).thenThrow(new TbRateLimitsException("Total message rate limit exceeded"));

        doPost(PUBLISH_URL, validRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value(33))
                .andExpect(jsonPath("$.message").value("Total message rate limit exceeded"));
    }

    @Test
    public void givenServiceRejectsRequest_whenPublish_thenBadRequestWithMessage() throws Exception {
        when(restPublishService.publish(any())).thenThrow(new DataValidationException("Topic name cannot contain wildcard characters!"));

        doPost(PUBLISH_URL, validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Topic name cannot contain wildcard characters!"));
    }

    @Test
    public void givenQueueFailure_whenPublish_thenServiceUnavailableWithErrorBody() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFailedFuture(new RuntimeException("queue unavailable")));

        doPostAsync(PUBLISH_URL, validRequest(), -1L)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Failed to publish the message: queue unavailable"))
                .andExpect(jsonPath("$.errorCode").value(2));
    }

    @Test
    public void givenQueueNeverAcks_whenPublish_thenServiceUnavailableOnTimeout() throws Exception {
        when(restPublishService.publish(any())).thenReturn(SettableFuture.create());

        // MockMvc never fires async timeouts itself, so drive the servlet timeout event the container would raise
        MvcResult started = doPost(PUBLISH_URL, validRequest()).andExpect(request().asyncStarted()).andReturn();
        MockAsyncContext asyncContext = (MockAsyncContext) started.getRequest().getAsyncContext();
        for (AsyncListener listener : asyncContext.getListeners()) {
            listener.onTimeout(new AsyncEvent(asyncContext));
        }

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Publish timed out waiting for the broker queue"));
    }

    @Test
    public void givenBlankTopic_whenPublish_thenBadRequest() throws Exception {
        RestPublishRequest request = validRequest();
        request.setTopic(" ");

        doPost(PUBLISH_URL, request).andExpect(status().isBadRequest());
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenMissingPayload_whenPublish_thenBadRequest() throws Exception {
        RestPublishRequest request = validRequest();
        request.setPayload(null);

        doPost(PUBLISH_URL, request).andExpect(status().isBadRequest());
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenInvalidQos_whenPublish_thenBadRequest() throws Exception {
        RestPublishRequest request = validRequest();
        request.setQos(3);

        doPost(PUBLISH_URL, request).andExpect(status().isBadRequest());
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenNegativeMessageExpiryInterval_whenPublish_thenBadRequest() throws Exception {
        RestPublishRequest request = validRequest();
        RestPublishProperties properties = new RestPublishProperties();
        properties.setMessageExpiryInterval(-1);
        request.setProperties(properties);

        doPost(PUBLISH_URL, request).andExpect(status().isBadRequest());
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenJsonObjectPayload_whenPublish_thenAcceptedAndReachesServiceAsJsonNode() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFuture(RestPublishResponse.success()));

        doPostRaw("{\"topic\":\"devices/a\",\"payload\":{\"cmd\":\"reboot\"},\"payloadEncoding\":\"JSON\"}").andExpect(status().isOk());

        ArgumentCaptor<RestPublishRequest> captor = ArgumentCaptor.forClass(RestPublishRequest.class);
        verify(restPublishService).publish(captor.capture());
        assertThat(captor.getValue().getPayload().isObject()).isTrue();
        assertThat(captor.getValue().getPayload().get("cmd").asText()).isEqualTo("reboot");
    }

    @Test
    public void givenUnknownPayloadEncoding_whenPublish_thenBadRequestWithoutJacksonInternals() throws Exception {
        doPostRaw("{\"topic\":\"devices/a\",\"payload\":\"x\",\"payloadEncoding\":\"HEX\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(allOf(
                        startsWith("Invalid request body:"),
                        containsString("\"HEX\""),
                        containsString("not one of the values accepted for Enum class"),
                        not(containsString("[Source:")))));
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenBodyOneByteOverLimit_whenPublish_thenPayloadTooLargeBeforeDeserialization() throws Exception {
        doPostRaw(bodyOfSize(MAX_REQUEST_BYTES + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value(31))
                .andExpect(jsonPath("$.message").value(containsString("max_payload_size is " + MAX_PAYLOAD_SIZE + " bytes")));
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenBodyExactlyAtLimit_whenPublish_thenNotRejectedBySizeGuard() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFuture(RestPublishResponse.success()));

        MvcResult result = doPostRaw(bodyOfSize(MAX_REQUEST_BYTES)).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    public void givenNoContentLength_whenPublish_thenLengthRequired() throws Exception {
        MockHttpServletRequestBuilder postRequest = post(PUBLISH_URL).contentType(MediaType.APPLICATION_JSON);
        setJwtToken(postRequest);

        mockMvc.perform(postRequest)
                .andExpect(status().isLengthRequired())
                .andExpect(jsonPath("$.errorCode").value(31))
                .andExpect(jsonPath("$.message").value("Content-Length header is required"));
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenBodyWithinLimit_whenPublish_thenNotRejectedBySizeGuard() throws Exception {
        when(restPublishService.publish(any())).thenReturn(Futures.immediateFuture(RestPublishResponse.success()));
        RestPublishRequest request = validRequest();
        request.setPayload(new TextNode("x".repeat(1024)));

        doPostAsync(PUBLISH_URL, request, -1L).andExpect(status().isOk());
    }

    @Test
    public void givenNoAuthentication_whenPublish_thenUnauthorized() throws Exception {
        logout();

        doPost(PUBLISH_URL, validRequest()).andExpect(status().isUnauthorized());
        verify(restPublishService, never()).publish(any());
    }

    @Test
    public void givenOpenApiDocs_whenReadPublishOperation_thenAllResponseCodesDocumented() throws Exception {
        doGet("/v3/api-docs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/mqtt/publish'].post.responses.keys()")
                        .value(containsInAnyOrder("200", "202", "400", "411", "413", "429", "503")))
                .andExpect(jsonPath("$.paths['/api/mqtt/publish'].post.responses['202'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/RestPublishResponse"))
                .andExpect(jsonPath("$.paths['/api/mqtt/publish'].post.responses['429'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/ThingsboardErrorResponse"));
    }

    /**
     * A TEXT publish whose serialized body is exactly {@code size} bytes; the payload string is the padding.
     */
    private static String bodyOfSize(long size) {
        String prefix = "{\"topic\":\"devices/a\",\"payloadEncoding\":\"TEXT\",\"payload\":\"";
        String suffix = "\"}";
        String body = prefix + "x".repeat((int) (size - prefix.length() - suffix.length())) + suffix;
        assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize((int) size);
        return body;
    }

    private ResultActions doPostRaw(String json) throws Exception {
        MockHttpServletRequestBuilder postRequest = post(PUBLISH_URL).contentType(MediaType.APPLICATION_JSON).content(json);
        setJwtToken(postRequest);
        return mockMvc.perform(postRequest);
    }

    private RestPublishRequest validRequest() {
        RestPublishRequest request = new RestPublishRequest();
        request.setTopic("devices/a/commands");
        request.setPayload(new TextNode("hello"));
        request.setQos(1);
        return request;
    }

}
