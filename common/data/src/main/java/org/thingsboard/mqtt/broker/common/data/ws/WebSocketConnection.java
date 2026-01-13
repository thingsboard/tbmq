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
package org.thingsboard.mqtt.broker.common.data.ws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.validation.Length;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serial;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class WebSocketConnection extends BaseData {

    @Serial
    private static final long serialVersionUID = -2995421139460181824L;

    @NoXss
    @Length
    private String name;
    private UUID userId;
    @Valid
    private transient WebSocketConnectionConfiguration configuration;
    @JsonIgnore
    private byte[] configurationBytes;

    public WebSocketConnectionConfiguration getConfiguration() {
        if (configuration != null) {
            return configuration;
        } else {
            if (configurationBytes != null) {
                try {
                    configuration = mapper.readValue(new ByteArrayInputStream(configurationBytes), WebSocketConnectionConfiguration.class);
                } catch (IOException e) {
                    log.warn("Can't deserialize WebSocket connection configuration: ", e);
                    return null;
                }
                return configuration;
            } else {
                return null;
            }
        }
    }

    public void setConfiguration(WebSocketConnectionConfiguration config) {
        this.configuration = config;
        try {
            this.configurationBytes = config != null ? mapper.writeValueAsBytes(config) : null;
        } catch (JsonProcessingException e) {
            log.warn("Can't serialize WebSocket connection configuration: ", e);
        }
    }

}
