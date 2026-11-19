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
package org.thingsboard.mqtt.broker.integration.service.trace;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceEventProto;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
@ConditionalOnProperty(prefix = "client-trace.writer", name = "enabled", havingValue = "true")
public class ClickHouseClientTraceWriter {

    private static final DateTimeFormatter CLICKHOUSE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    @Value("${client-trace.clickhouse.url:http://localhost:8123}")
    private String url;
    @Value("${client-trace.clickhouse.database:default}")
    private String database;
    @Value("${client-trace.clickhouse.username:default}")
    private String username;
    @Value("${client-trace.clickhouse.password:}")
    private String password;
    @Value("${client-trace.clickhouse.retention-days:7}")
    private int retentionDays;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @PostConstruct
    void initialize() throws Exception {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("client-trace.clickhouse.retention-days must be positive");
        }
        execute("""
                CREATE TABLE IF NOT EXISTS client_trace_event (
                  event_id UUID, trace_id UUID, client_id String, session_id UUID, ts DateTime64(3),
                  direction LowCardinality(String), packet_type LowCardinality(String), topic String,
                  qos UInt8, packet_id UInt32, payload_size UInt32, remote_address String,
                  details String, service_id String
                ) ENGINE = MergeTree ORDER BY (client_id, ts)
                TTL toDateTime(ts) + INTERVAL %d DAY
                """.formatted(retentionDays));
        execute("ALTER TABLE client_trace_event ADD COLUMN IF NOT EXISTS event_id UUID FIRST");
    }

    public void insert(List<ClientTraceEventProto> events) throws Exception {
        StringBuilder body = new StringBuilder("INSERT INTO client_trace_event FORMAT JSONEachRow\n");
        for (ClientTraceEventProto event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event_id", event.getEventId());
            row.put("trace_id", event.getTraceId());
            row.put("client_id", event.getClientId());
            row.put("session_id", event.getSessionId());
            row.put("ts", CLICKHOUSE_TIMESTAMP.format(Instant.ofEpochMilli(event.getTs())));
            row.put("direction", event.getDirection());
            row.put("packet_type", event.getPacketType());
            row.put("topic", event.getTopic());
            row.put("qos", event.getQos());
            row.put("packet_id", event.getPacketId());
            row.put("payload_size", event.getPayloadSize());
            row.put("remote_address", event.getRemoteAddress());
            row.put("details", event.getDetails());
            row.put("service_id", event.getServiceId());
            body.append(JacksonUtil.toString(row)).append('\n');
        }
        execute(body.toString());
    }

    private void execute(String sql) throws Exception {
        String endpoint = url + "/?database=" + URLEncoder.encode(database, StandardCharsets.UTF_8);
        String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString(sql)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("ClickHouse returned " + response.statusCode() + ": " + response.body());
        }
    }
}
