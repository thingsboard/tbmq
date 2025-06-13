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
package org.thingsboard.mqtt.broker.service.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ServiceInfoDto {

    @Schema(description = "Service Id.")
    private String serviceId;
    @Schema(description = "Service type.")
    private String serviceType;
    @Schema(description = "Last update time.")
    private Long lastUpdateTime;
    @Schema(description = "CPU usage, in percent.")
    private Long cpuUsage;
    @Schema(description = "Total CPU count.")
    private Long cpuCount;
    @Schema(description = "Memory usage, in percent.")
    private Long memoryUsage;
    @Schema(description = "Total memory in bytes.")
    private Long totalMemory;
    @Schema(description = "Disk usage, in percent.")
    private Long diskUsage;
    @Schema(description = "Total disk space in bytes.")
    private Long totalDiskSpace;
    @Schema(description = "Current service status based on last update time.")
    private ServiceStatus status;

    public boolean isDataPresent() {
        return cpuUsage != null || cpuCount != null || memoryUsage != null || totalMemory != null || diskUsage != null || totalDiskSpace != null;
    }
}
