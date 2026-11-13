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
package org.thingsboard.mqtt.broker.service.trace;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "client-trace.clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseClientTraceStore {
    @Value("${client-trace.clickhouse.url:http://localhost:8123}") private String url;
    @Value("${client-trace.clickhouse.database:default}") private String database;
    @Value("${client-trace.clickhouse.username:default}") private String username;
    @Value("${client-trace.clickhouse.password:}") private String password;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public List<JsonNode> find(String clientId, long from, long to, int limit) throws Exception {
        return find(clientId, from, to, limit, false);
    }

    public List<JsonNode> findSince(String clientId, long from, long to, int limit) throws Exception {
        return find(clientId, from, to, limit, true);
    }

    private List<JsonNode> find(String clientId, long from, long to, int limit, boolean ascending) throws Exception {
        String safeClientId = clientId.replace("\\", "\\\\").replace("'", "\\'");
        String sql = "SELECT *, toUnixTimestamp64Milli(ts) AS ts_ms FROM client_trace_event WHERE client_id='" + safeClientId
                + "' AND ts >= fromUnixTimestamp64Milli(" + from + ") AND ts <= fromUnixTimestamp64Milli(" + to
                + ") ORDER BY ts " + (ascending ? "ASC" : "DESC") + " LIMIT "
                + Math.min(Math.max(limit, 1), 5000) + " FORMAT JSONEachRow";
        String response = execute(sql);
        List<JsonNode> result = new ArrayList<>();
        response.lines().filter(line -> !line.isBlank()).forEach(line -> result.add(JacksonUtil.toJsonNode(line)));
        return result;
    }

    private String execute(String sql) throws Exception {
        String endpoint = url + "/?database=" + URLEncoder.encode(database, StandardCharsets.UTF_8);
        String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + auth).POST(HttpRequest.BodyPublishers.ofString(sql)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) throw new IllegalStateException("ClickHouse returned " + response.statusCode() + ": " + response.body());
        return response.body();
    }
}
