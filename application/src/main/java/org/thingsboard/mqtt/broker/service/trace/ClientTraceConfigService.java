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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientTraceConfigService {
    private final JdbcTemplate jdbcTemplate;
    private final InternodeNotificationsService notificationsService;

    @Transactional
    public ClientTraceConfig save(ClientTraceConfig config) {
        if (config.getId() == null) {
            config.setId(UUID.randomUUID());
        }
        config.setCreatedTime(System.currentTimeMillis());
        jdbcTemplate.update("""
                insert into client_trace(id, created_time, client_id, expires_at, trace_level)
                values (?, ?, ?, ?, ?)
                on conflict (client_id) do update set id=excluded.id, created_time=excluded.created_time,
                  expires_at=excluded.expires_at, trace_level=excluded.trace_level
                """, config.getId(), config.getCreatedTime(), config.getClientId(),
                Timestamp.from(config.getExpiresAt()), config.getLevel().name());
        broadcast(config, false);
        return config;
    }

    public List<ClientTraceConfig> findAll() {
        return jdbcTemplate.query("select id, created_time, client_id, expires_at, trace_level from client_trace order by created_time desc",
                (rs, rowNum) -> {
                    ClientTraceConfig config = new ClientTraceConfig();
                    config.setId(UUID.fromString(rs.getString("id")));
                    config.setCreatedTime(rs.getLong("created_time"));
                    config.setClientId(rs.getString("client_id"));
                    config.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
                    config.setLevel(ClientTraceLevel.valueOf(rs.getString("trace_level")));
                    return config;
                });
    }

    @Transactional
    public void delete(UUID id) {
        List<ClientTraceConfig> configs = jdbcTemplate.query("select id, created_time, client_id, expires_at, trace_level from client_trace where id=?",
                (rs, rowNum) -> {
                    ClientTraceConfig c = new ClientTraceConfig();
                    c.setId(id); c.setClientId(rs.getString("client_id"));
                    c.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
                    c.setLevel(ClientTraceLevel.valueOf(rs.getString("trace_level")));
                    return c;
                }, id);
        if (!configs.isEmpty()) {
            jdbcTemplate.update("delete from client_trace where id=?", id);
            broadcast(configs.get(0), true);
        }
    }

    private void broadcast(ClientTraceConfig config, boolean deleted) {
        ClientTraceConfigProto update = ClientTraceConfigProto.newBuilder()
                .setTraceId(config.getId().toString()).setClientId(config.getClientId())
                .setExpiresAt(config.getExpiresAt().toEpochMilli()).setLevel(config.getLevel().name())
                .setDeleted(deleted).build();
        notificationsService.broadcast(InternodeNotificationProto.newBuilder().setClientTraceConfigProto(update).build());
    }
}
