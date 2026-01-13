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
package org.thingsboard.mqtt.broker.service.state;

import com.google.common.util.concurrent.SettableFuture;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;

import java.util.UUID;

@Data
public class ValidationTask {

    private final UUID uuid = UUID.randomUUID();
    private final long ts = System.currentTimeMillis();
    private final SettableFuture<Void> future = SettableFuture.create();
    private final ValidationTaskType type;
    private final Integration configuration;

}
