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
package org.thingsboard.mqtt.broker.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ClientSessionNodeStatsInfoDto extends ClientSessionStatsInfoDto {

    private final long deviceCount;
    private final long applicationCount;
    private final long integrationCount;

    public ClientSessionNodeStatsInfoDto(long connectedCount, long disconnectedCount, long totalCount,
                                         long deviceCount, long applicationCount, long integrationCount) {
        super(connectedCount, disconnectedCount, totalCount);
        this.deviceCount = deviceCount;
        this.applicationCount = applicationCount;
        this.integrationCount = integrationCount;
    }

}
