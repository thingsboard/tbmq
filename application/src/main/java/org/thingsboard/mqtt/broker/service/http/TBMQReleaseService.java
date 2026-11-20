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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.provider.AbstractServiceProvider;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.LATEST_VERSION_AVAILABLE_TOPIC_NAME;

@Service
@Slf4j
public class TBMQReleaseService extends AbstractServiceProvider {

    private static final String TBMQ_GITHUB_API_URL = "https://api.github.com/repos/thingsboard/tbmq/releases/latest";
    private static final int VERSION_CHECK_INTERVAL = 3 * 60 * 60;
    private static final long BASE_WAIT_MS = TimeUnit.SECONDS.toMillis(VERSION_CHECK_INTERVAL);
    private static final long MAX_WAIT_MS = TimeUnit.HOURS.toMillis(24);
    // The scheduler may fire a little earlier than the recorded attempt time plus the wait; do not skip a run for that.
    private static final long SCHEDULE_JITTER_MS = TimeUnit.MINUTES.toMillis(1);

    private final RetainedMsgListenerService retainedMsgListenerService;
    private final boolean enabled;
    private final LongSupplier clock;

    private HttpClient client;
    private HttpRequest request;

    // Touched only by the single scheduler thread (the method is never invoked concurrently with itself).
    private long waitMs = BASE_WAIT_MS;
    private long nextAttemptAtMs;

    @Autowired
    public TBMQReleaseService(SystemInfoService systemInfoService,
                              ServiceInfoProvider serviceInfoProvider,
                              RetainedMsgListenerService retainedMsgListenerService,
                              @Value("${app.version-check.enabled:true}") boolean enabled) {
        this(systemInfoService, serviceInfoProvider, retainedMsgListenerService, enabled, System::currentTimeMillis);
    }

    TBMQReleaseService(SystemInfoService systemInfoService,
                       ServiceInfoProvider serviceInfoProvider,
                       RetainedMsgListenerService retainedMsgListenerService,
                       boolean enabled,
                       LongSupplier clock) {
        super(systemInfoService, serviceInfoProvider);
        this.retainedMsgListenerService = retainedMsgListenerService;
        this.enabled = enabled;
        this.clock = clock;
    }

    void setHttpClient(HttpClient client) {
        this.client = client;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("The check for the latest TBMQ release is disabled (app.version-check.enabled=false).");
            return;
        }
        client = HttpClient.newHttpClient();
        request = HttpRequest.newBuilder()
                .uri(URI.create(TBMQ_GITHUB_API_URL))
                .timeout(Duration.of(30, SECONDS))
                .GET()
                .build();
    }

    @Scheduled(initialDelay = 5, fixedRate = VERSION_CHECK_INTERVAL, timeUnit = TimeUnit.SECONDS)
    public void scheduleVersionCheck() {
        if (!enabled) {
            return;
        }
        // read once, before the request: a failure backs off from the attempt, however long the request took
        long now = clock.getAsLong();
        try {
            if (!isCurrentNodeShouldCheckAvailableVersion()) {
                return;
            }
            if (!retainedMsgListenerService.isInitialized()) {
                return;
            }
            if (now + SCHEDULE_JITTER_MS < nextAttemptAtMs) {
                return;
            }
            log.info("Executing get latest version of TBMQ release from GitHub.");
            HttpResponse<String> response = sendRequest();
            if (response.statusCode() == 200) {
                String version = getVersion(response);
                retainedMsgListenerService.cacheRetainedMsgAndPersist(LATEST_VERSION_AVAILABLE_TOPIC_NAME, newRetainedMsg(version));

                log.info("Got latest version of TBMQ release from GitHub: {}", version);
                waitMs = BASE_WAIT_MS;
                nextAttemptAtMs = now + waitMs;
            } else {
                log.debug("Failed to get the latest release version, reason: {} {}", response.statusCode(), response.body());
                backOff(now);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while getting the latest release version", e);
        } catch (IOException e) {
            log.debug("Failed to get the latest release version", e);
            backOff(now);
        }
    }

    private void backOff(long now) {
        waitMs = Math.min(waitMs * 2, MAX_WAIT_MS);
        nextAttemptAtMs = now + waitMs;
    }

    private String getVersion(HttpResponse<String> response) {
        JsonNode jsonNode = JacksonUtil.toJsonNode(response.body());
        return jsonNode.get("tag_name").asText().replaceAll("v", "");
    }

    private HttpResponse<String> sendRequest() throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private RetainedMsg newRetainedMsg(String version) {
        return new RetainedMsg(LATEST_VERSION_AVAILABLE_TOPIC_NAME, version.getBytes(StandardCharsets.UTF_8));
    }
}
