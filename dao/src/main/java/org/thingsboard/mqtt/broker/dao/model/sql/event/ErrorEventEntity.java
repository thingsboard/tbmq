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
package org.thingsboard.mqtt.broker.dao.model.sql.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;

import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.ERROR_EVENT_TABLE_NAME;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.EVENT_ERROR_COLUMN_NAME;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.EVENT_METHOD_COLUMN_NAME;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ERROR_EVENT_TABLE_NAME)
@NoArgsConstructor
public class ErrorEventEntity extends EventEntity<ErrorEvent> {

    @Column(name = EVENT_METHOD_COLUMN_NAME)
    private String method;
    @Column(name = EVENT_ERROR_COLUMN_NAME)
    private String error;

    public ErrorEventEntity(ErrorEvent event) {
        super(event);
        this.method = event.getMethod();
        this.error = event.getError();
    }

    @Override
    public ErrorEvent toData() {
        return ErrorEvent.builder()
                .entityId(entityId)
                .serviceId(serviceId)
                .id(id)
                .ts(ts)
                .method(method)
                .error(error)
                .build();
    }

}
