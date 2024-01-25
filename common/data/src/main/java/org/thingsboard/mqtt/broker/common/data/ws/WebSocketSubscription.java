/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.ws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import javax.validation.Valid;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class WebSocketSubscription extends BaseData {

    @NoXss
    private String name;
    private UUID wsConnectionId;
    @Valid
    private transient WebSocketSubscriptionConfiguration configuration;
    @JsonIgnore
    private byte[] configurationBytes;

}
