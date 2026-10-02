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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thingsboard.mqtt.broker.service.trace.ClickHouseClientTraceStore;
import org.thingsboard.mqtt.broker.service.trace.ClientTraceConfig;
import org.thingsboard.mqtt.broker.service.trace.ClientTraceConfigService;
import org.thingsboard.mqtt.broker.service.trace.ClientTraceStream;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client-traces")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYS_ADMIN')")
public class ClientTraceController extends BaseController {
    private final ClientTraceConfigService configService;
    private final ObjectProvider<ClickHouseClientTraceStore> storeProvider;
    private final ClientTraceStream stream;

    @GetMapping
    public List<ClientTraceConfig> getConfigs() {
        return configService.findAll();
    }

    @PostMapping
    public ClientTraceConfig create(@Valid @RequestBody ClientTraceConfig config) {
        return configService.save(config);
    }

    @DeleteMapping("/{traceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID traceId) {
        configService.delete(traceId);
    }

    @GetMapping("/{clientId}/events")
    public List<JsonNode> getEvents(@PathVariable String clientId, @RequestParam long from, @RequestParam long to,
                                    @RequestParam(defaultValue = "1000") int limit) throws Exception {
        ClickHouseClientTraceStore store = storeProvider.getIfAvailable();
        if (store == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ClickHouse trace storage is disabled");
        return store.find(clientId, from, to, limit);
    }

    @GetMapping(value = "/{clientId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String clientId) {
        ClickHouseClientTraceStore store = storeProvider.getIfAvailable();
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ClickHouse trace storage is disabled");
        }
        return stream.subscribe(clientId, store);
    }
}
