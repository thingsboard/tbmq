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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.config.annotations.ApiOperation;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainService;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainStatus;

@RestController
@RequiredArgsConstructor
public class NodeDrainController extends BaseController {

    public static final String DRAIN_PATH = "/api/node/drain";

    private final NodeDrainService nodeDrainService;

    @ApiOperation(value = "Start draining this broker node (startNodeDrain)",
            notes = "Marks this broker node out of service, rejects new MQTT connections, and disconnects current " +
                    "sessions in configured batches. Repeated calls are idempotent.")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(DRAIN_PATH)
    public NodeDrainStatus startDrain() {
        return nodeDrainService.startDrain();
    }

    @ApiOperation(value = "Get this broker node drain status (getNodeDrainStatus)",
            notes = "Returns the current local drain state and session progress.")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(DRAIN_PATH)
    public NodeDrainStatus getDrainStatus() {
        return nodeDrainService.getStatus();
    }

}
