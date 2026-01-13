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
package org.thingsboard.mqtt.broker.common.data.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.validation.Length;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.io.Serial;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class Integration extends BaseData {

    @Serial
    private static final long serialVersionUID = 1934983577296873728L;

    @NoXss
    @Length
    private String name;
    private IntegrationType type;
    private boolean enabled;
    private long disconnectedTime;

    private JsonNode configuration;
    @NoXss
    private JsonNode additionalInfo;

    private transient ObjectNode status;

    public Integration() {
        super();
    }

    public Integration(UUID id) {
        super(id);
    }

    public Integration(Integration integration) {
        super(integration);
        this.name = integration.getName();
        this.type = integration.getType();
        this.enabled = integration.isEnabled();
        this.disconnectedTime = integration.getDisconnectedTime();
        this.configuration = integration.getConfiguration();
        this.additionalInfo = integration.getAdditionalInfo();
        this.status = integration.getStatus();
    }

    @JsonIgnore
    public String getIdStr() {
        return getId().toString();
    }
}
