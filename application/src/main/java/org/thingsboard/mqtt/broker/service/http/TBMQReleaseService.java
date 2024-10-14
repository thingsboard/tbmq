/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.provider.AbstractServiceProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.LATEST_VERSION_AVAILABLE_TOPIC_NAME;

@Service
@Slf4j
public class TBMQReleaseService extends AbstractServiceProvider {

    private static final String TBMQ_GITHUB_API_URL = "https://api.github.com/repos/thingsboard/tbmq/releases/latest";

    private final RetainedMsgListenerService retainedMsgListenerService;

    private HttpClient client;
    private HttpRequest request;

    @Autowired
    public TBMQReleaseService(TbQueueAdmin tbQueueAdmin,
                              ServiceInfoProvider serviceInfoProvider,
                              RetainedMsgListenerService retainedMsgListenerService) {
        super(tbQueueAdmin, serviceInfoProvider);
        this.retainedMsgListenerService = retainedMsgListenerService;
    }

    @PostConstruct
    public void init() {
        client = HttpClient.newHttpClient();
        request = HttpRequest.newBuilder()
                .uri(URI.create(TBMQ_GITHUB_API_URL))
                .timeout(Duration.of(10, SECONDS))
                .GET()
                .build();
    }

    @Scheduled(initialDelay = 1, fixedRate = 3 * 60 * 60, timeUnit = TimeUnit.SECONDS)
    public void scheduleVersionCheck() {
        try {
            if (!isCurrentNodeShouldCheckAvailableVersion()) {
                return;
            }
            log.info("Executing get latest version of TBMQ release from GitHub.");
            HttpResponse<String> response = sendRequest();
            if (response.statusCode() == 200) {
                String version = getVersion(response);
                retainedMsgListenerService.cacheRetainedMsgAndPersist(LATEST_VERSION_AVAILABLE_TOPIC_NAME, newRetainedMsg(version));

                log.info("Got latest version of TBMQ release from GitHub: {}", version);
            } else {
                log.error("Failed to get the latest release version, reason: {} {}", response.statusCode(), response.body());
            }
        } catch (Throwable e) {
            log.error("Failed to get the latest release version", e);
        }
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
