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
package org.thingsboard.mqtt.broker.service.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.LATEST_VERSION_AVAILABLE_TOPIC_NAME;

class TBMQReleaseServiceTest {

    private static final long HOUR_MS = Duration.ofHours(1).toMillis();

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private SystemInfoService systemInfoService;
    private ServiceInfoProvider serviceInfoProvider;
    private RetainedMsgListenerService retainedMsgListenerService;
    private HttpClient httpClient;

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> appender;
    private boolean additivityBefore;

    @BeforeEach
    void setUp() {
        systemInfoService = mock(SystemInfoService.class);
        serviceInfoProvider = mock(ServiceInfoProvider.class);
        retainedMsgListenerService = mock(RetainedMsgListenerService.class);
        httpClient = mock(HttpClient.class);
        when(serviceInfoProvider.getServiceId()).thenReturn("node-1");
        when(systemInfoService.getTbmqServiceIds()).thenReturn(List.of("node-1"));
        when(retainedMsgListenerService.isInitialized()).thenReturn(true);

        serviceLogger = (Logger) LoggerFactory.getLogger(TBMQReleaseService.class);
        additivityBefore = serviceLogger.isAdditive();
        serviceLogger.setAdditive(false);
        serviceLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        serviceLogger.setAdditive(additivityBefore);
        serviceLogger.setLevel(null);
    }

    @Test
    void givenDisabled_whenScheduled_thenNoHttpCallAndOneInfoAtStartup() throws Exception {
        TBMQReleaseService service = newService(false);

        service.scheduleVersionCheck();
        now.addAndGet(3 * HOUR_MS);
        service.scheduleVersionCheck();

        verify(httpClient, never()).send(any(), any());
        verify(retainedMsgListenerService, never()).cacheRetainedMsgAndPersist(anyString(), any());
        assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.INFO).hasSize(1);
        assertThat(appender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @Test
    void givenThreeFailures_whenScheduledEveryThreeHours_thenAttemptsAt0And6And18Hours() throws Exception {
        TBMQReleaseService service = newService(true);
        // failures: non-200, exception, non-200
        stubResponses(response(500, "boom"), new IOException("down"), response(503, "later"));

        long start = now.get();
        List<Long> attemptHours = new java.util.ArrayList<>();
        for (int hour = 0; hour <= 21; hour += 3) {
            now.set(start + hour * HOUR_MS);
            int before = sendCount();
            service.scheduleVersionCheck();
            if (sendCount() > before) {
                attemptHours.add((long) hour);
            }
        }

        assertThat(attemptHours).containsExactly(0L, 6L, 18L);
        assertThat(appender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
        verify(retainedMsgListenerService, never()).cacheRetainedMsgAndPersist(anyString(), any());
    }

    @Test
    void givenASlowExceptionalFailure_whenScheduled_thenTheBackoffCountsFromTheAttemptNotFromTheFailure() throws Exception {
        TBMQReleaseService service = newService(true);
        long start = now.get();
        // the request takes ten minutes before it fails
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            now.addAndGet(10 * Duration.ofMinutes(1).toMillis());
            throw new IOException("timed out");
        });

        service.scheduleVersionCheck();
        now.set(start + 6 * HOUR_MS);
        service.scheduleVersionCheck();

        // 6 h after the first attempt started, as after a non-200 answer
        assertThat(sendCount()).isEqualTo(2);
    }

    @Test
    void givenFailuresKeepGoing_whenScheduled_thenWaitIsCappedAt24Hours() throws Exception {
        TBMQReleaseService service = newService(true);
        stubResponses(response(500, "x"));

        long start = now.get();
        List<Long> attemptHours = new java.util.ArrayList<>();
        for (int hour = 0; hour <= 72; hour += 3) {
            now.set(start + hour * HOUR_MS);
            int before = sendCount();
            service.scheduleVersionCheck();
            if (sendCount() > before) {
                attemptHours.add((long) hour);
            }
        }

        // waits after each failure: 6 h, 12 h, 24 h, 24 h, 24 h
        assertThat(attemptHours).containsExactly(0L, 6L, 18L, 42L, 66L);
    }

    @Test
    void givenSuccessAfterFailure_whenScheduled_thenIntervalResetsToThreeHours() throws Exception {
        TBMQReleaseService service = newService(true);
        stubResponses(response(500, "x"), response(200, "{\"tag_name\":\"v2.9.9\"}"), response(500, "x"));

        long start = now.get();
        List<Long> attemptHours = new java.util.ArrayList<>();
        for (int hour = 0; hour <= 15; hour += 3) {
            now.set(start + hour * HOUR_MS);
            int before = sendCount();
            service.scheduleVersionCheck();
            if (sendCount() > before) {
                attemptHours.add((long) hour);
            }
        }

        // 0 h fails (wait 6 h), 6 h succeeds (wait 3 h), 9 h fails (wait 6 h), 15 h fails again
        assertThat(attemptHours).containsExactly(0L, 6L, 9L, 15L);
        verify(retainedMsgListenerService, times(1)).cacheRetainedMsgAndPersist(eq(LATEST_VERSION_AVAILABLE_TOPIC_NAME), any());
        assertThat(appender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @Test
    void givenSuccess_whenScheduled_thenLogsAsBefore() throws Exception {
        TBMQReleaseService service = newService(true);
        stubResponses(response(200, "{\"tag_name\":\"v2.9.9\"}"));

        service.scheduleVersionCheck();

        List<String> infos = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO).map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(infos).containsExactly(
                "Executing get latest version of TBMQ release from GitHub.",
                "Got latest version of TBMQ release from GitHub: 2.9.9");
    }

    private TBMQReleaseService newService(boolean enabled) {
        TBMQReleaseService service = new TBMQReleaseService(systemInfoService, serviceInfoProvider,
                retainedMsgListenerService, enabled, now::get);
        service.init();
        service.setHttpClient(httpClient);
        return service;
    }

    private int sendCount() {
        return org.mockito.Mockito.mockingDetails(httpClient).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send")).toList().size();
    }

    /** Each element is returned (or thrown) in turn; the last one repeats. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubResponses(Object... outcomes) throws Exception {
        OngoingStubbing stubbing = when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)));
        for (Object outcome : outcomes) {
            stubbing = outcome instanceof Throwable t ? stubbing.thenThrow(t) : stubbing.thenReturn(outcome);
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
