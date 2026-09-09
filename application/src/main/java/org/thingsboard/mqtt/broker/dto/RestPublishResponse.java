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

import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Outcome of a REST publish. The message was accepted by the broker; this does not confirm delivery to any client.")
public class RestPublishResponse {

    @Schema(description = "MQTT PUBACK reason code: 0 - success, 16 - no matching subscribers (the message was still processed, e.g. stored as retained).", example = "0")
    private int reasonCode;
    @Schema(description = "Human-readable description of the reason code.", example = "Success")
    private String message;

    public static RestPublishResponse success() {
        return new RestPublishResponse(MqttReasonCodes.PubAck.SUCCESS.byteValue(), "Success");
    }

    public static RestPublishResponse noMatchingSubscribers() {
        return new RestPublishResponse(MqttReasonCodes.PubAck.NO_MATCHING_SUBSCRIBERS.byteValue(), "No matching subscribers");
    }

    public boolean isSuccess() {
        return reasonCode == MqttReasonCodes.PubAck.SUCCESS.byteValue();
    }

}
