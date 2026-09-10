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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MQTT 5 PUBLISH properties. Every field is optional.")
public class RestPublishProperties {

    @Min(0)
    @Max(1)
    @Schema(description = "Payload Format Indicator: 0 - unspecified bytes, 1 - UTF-8 encoded character data.", example = "1")
    private Integer payloadFormatIndicator;

    @PositiveOrZero
    @Schema(description = "Message Expiry Interval in seconds.", example = "3600")
    private Integer messageExpiryInterval;

    @Size(max = 65535)
    @Schema(description = "Content Type of the payload.", example = "application/json")
    private String contentType;

    @Size(max = 65535)
    @Schema(description = "Response Topic for request/response messaging. Must be a valid topic name (no wildcards).",
            example = "devices/a/replies")
    private String responseTopic;

    @Schema(description = "Correlation Data, Base64-encoded.", example = "cmVxLTQy")
    private String correlationData;

    @Schema(description = "User Properties as key-value pairs.", example = "{\"source\": \"backend\"}")
    private Map<String, String> userProperties;

}
