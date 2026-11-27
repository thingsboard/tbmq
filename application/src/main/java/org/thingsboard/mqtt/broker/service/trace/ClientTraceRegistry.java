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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceConfigProto;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ClientTraceRegistry {
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, ActiveTrace> traces = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        jdbcTemplate.query("select id, client_id, expires_at, trace_level from client_trace where expires_at > now()",
                (rs, rowNum) -> new ActiveTrace(UUID.fromString(rs.getString("id")), rs.getString("client_id"),
                        rs.getTimestamp("expires_at").toInstant(), ClientTraceLevel.valueOf(rs.getString("trace_level"))))
                .forEach(this::put);
    }

    public Optional<ActiveTrace> get(String clientId) {
        ActiveTrace trace = traces.get(clientId);
        if (trace != null && !trace.expiresAt().isAfter(Instant.now())) {
            traces.remove(clientId, trace);
            trace = null;
        }
        return Optional.ofNullable(trace);
    }

    public void apply(ClientTraceConfigProto proto) {
        if (proto.getDeleted() || proto.getExpiresAt() <= System.currentTimeMillis()) {
            traces.remove(proto.getClientId());
        } else {
            put(new ActiveTrace(UUID.fromString(proto.getTraceId()), proto.getClientId(),
                    Instant.ofEpochMilli(proto.getExpiresAt()), ClientTraceLevel.valueOf(proto.getLevel())));
        }
    }

    private void put(ActiveTrace trace) {
        traces.put(trace.clientId(), trace);
    }

    public record ActiveTrace(UUID id, String clientId, Instant expiresAt, ClientTraceLevel level) {}
}
