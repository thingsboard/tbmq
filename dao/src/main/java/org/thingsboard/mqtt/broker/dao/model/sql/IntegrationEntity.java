/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.model.BaseSqlEntity;
import org.thingsboard.mqtt.broker.dao.model.ModelConstants;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_ADDITIONAL_INFO_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_CONFIGURATION_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_ENABLED_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_NAME_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_STATUS_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_TABLE_NAME;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.INTEGRATION_TYPE_PROPERTY;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = INTEGRATION_TABLE_NAME)
public class IntegrationEntity extends BaseSqlEntity<Integration> {

    @Column(name = INTEGRATION_NAME_PROPERTY)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = INTEGRATION_TYPE_PROPERTY)
    private IntegrationType type;

    @Column(name = INTEGRATION_ENABLED_PROPERTY)
    private Boolean enabled;

    @Column(name = ModelConstants.INTEGRATION_DISCONNECTED_TIME_PROPERTY)
    private long disconnectedTime;

    @Convert(converter = JsonConverter.class)
    @Column(name = INTEGRATION_CONFIGURATION_PROPERTY)
    private JsonNode configuration;

    @Convert(converter = JsonConverter.class)
    @Column(name = INTEGRATION_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    @Column(name = INTEGRATION_STATUS_PROPERTY)
    private String status;

    public IntegrationEntity() {
        super();
    }

    public IntegrationEntity(Integration integration) {
        super(integration);
        this.name = integration.getName();
        this.type = integration.getType();
        this.enabled = integration.isEnabled();
        this.disconnectedTime = integration.getDisconnectedTime();
        this.configuration = integration.getConfiguration();
        this.additionalInfo = integration.getAdditionalInfo();
        this.status = integration.getStatus() != null ? JacksonUtil.toString(integration.getStatus()) : null;
    }

    @Override
    public Integration toData() {
        Integration integration = new Integration((id));
        integration.setCreatedTime(this.createdTime);
        integration.setName(name);
        integration.setType(type);
        integration.setEnabled(enabled);
        integration.setDisconnectedTime(disconnectedTime);
        integration.setConfiguration(configuration);
        integration.setAdditionalInfo(additionalInfo);

        if (StringUtils.isNotEmpty(status)) {
            integration.setStatus(JacksonUtil.fromString(status, ObjectNode.class));
        }

        return integration;
    }

}
