/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.session;

import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public class DisconnectReason {
    private final DisconnectReasonType type;
    private String message;

    public DisconnectReason(DisconnectReasonType type) {
        this.type = type;
    }

    public DisconnectReason(DisconnectReasonType type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public String toString() {
        if (StringUtils.isEmpty(message)) {
            return type.toString();
        } else {
            return type + "(" + message + ")";
        }
    }
}
