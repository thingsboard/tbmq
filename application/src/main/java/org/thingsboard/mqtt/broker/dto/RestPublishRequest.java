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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

@Data
@Schema(description = "MQTT message to publish through the broker.")
public class RestPublishRequest {

    @NotBlank
    @NoXss
    @Schema(description = "Topic name to publish to. Wildcards are not allowed.", example = "devices/a/commands", requiredMode = Schema.RequiredMode.REQUIRED)
    private String topic;

    // deliberately no @NoXss: the payload is opaque data, and AntiSamy rejects any markup (XML/HTML telemetry)
    @NotNull
    @JsonSetter(nulls = Nulls.FAIL) // a JSON null would otherwise bind to NullNode and pass @NotNull
    @Schema(description = "Message payload, in the form declared by 'payloadEncoding'. " +
            "An empty payload with 'retain' set clears the retained message.",
            example = "eyJjbWQiOiAicmVib290In0=", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode payload;

    @Schema(description = "Form of the 'payload' field: BASE64 (default) - a Base64 string of the raw message bytes; " +
            "TEXT - a string, published as its UTF-8 bytes; " +
            "JSON - any JSON value (object, array, string, number, boolean), published as its compact JSON text.",
            example = "BASE64", defaultValue = "BASE64")
    private PayloadEncoding payloadEncoding = PayloadEncoding.BASE64;

    @Min(0)
    @Max(2)
    @Schema(description = "Quality of Service level: 0, 1 or 2.", example = "1", defaultValue = "0")
    private int qos;

    @Schema(description = "Whether the message should be stored as the retained message for the topic.", example = "false", defaultValue = "false")
    private boolean retain;

    @Valid
    private RestPublishProperties properties;

}
