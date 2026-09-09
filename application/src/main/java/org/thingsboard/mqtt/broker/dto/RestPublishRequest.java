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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "MQTT message to publish through the broker.")
public class RestPublishRequest {

    @NotBlank
    @Schema(description = "Topic name to publish to. Wildcards are not allowed.", example = "devices/a/commands", requiredMode = Schema.RequiredMode.REQUIRED)
    private String topic;

    @NotNull
    @Schema(description = "Message payload. Interpreted according to 'payloadEncoding'. An empty string with 'retain' set clears the retained message.",
            example = "{\"cmd\": \"reboot\"}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payload;

    @Schema(description = "Payload encoding: PLAIN (default) publishes the UTF-8 bytes of 'payload'; BASE64 publishes its Base64-decoded bytes.",
            example = "PLAIN", defaultValue = "PLAIN")
    private PayloadEncoding payloadEncoding = PayloadEncoding.PLAIN;

    @Min(0)
    @Max(2)
    @Schema(description = "Quality of Service level: 0, 1 or 2.", example = "1", defaultValue = "0")
    private int qos;

    @Schema(description = "Whether the message should be stored as the retained message for the topic.", example = "false", defaultValue = "false")
    private boolean retain;

    @Valid
    private RestPublishProperties properties;

}
