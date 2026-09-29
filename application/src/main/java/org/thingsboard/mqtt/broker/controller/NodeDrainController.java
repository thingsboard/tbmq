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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.config.annotations.ApiOperation;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainService;
import org.thingsboard.mqtt.broker.service.drain.NodeDrainStatus;

@RestController
@RequiredArgsConstructor
public class NodeDrainController extends BaseController {

    public static final String DRAIN_PATH = "/api/node/drain";

    private final NodeDrainService nodeDrainService;
    private final ServiceInfoProvider serviceInfoProvider;

    @ApiOperation(value = "Start draining this broker node (startNodeDrain)",
            notes = "Marks this broker node out of service, rejects new MQTT connections, and disconnects current " +
                    "sessions in configured batches. The expected service ID must match this node. " +
                    "Repeated calls are idempotent.")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(DRAIN_PATH)
    public NodeDrainStatus startDrain(@RequestParam(value = "expectedServiceId", required = false) String expectedServiceId) throws ThingsboardException {
        validateExpectedServiceId(expectedServiceId);
        return nodeDrainService.startDrain();
    }

    @ApiOperation(value = "Get this broker node drain status (getNodeDrainStatus)",
            notes = "Returns the current local drain state and session progress.")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(DRAIN_PATH)
    public NodeDrainStatus getDrainStatus() {
        return nodeDrainService.getStatus();
    }

    @ApiOperation(value = "Cancel draining this broker node (cancelNodeDrain)",
            notes = "Returns this broker node to ACTIVE state after verifying the expected service ID. " +
                    "Sessions already disconnected by the drain are not restored.")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(DRAIN_PATH)
    public NodeDrainStatus cancelDrain(@RequestParam(value = "expectedServiceId", required = false) String expectedServiceId) throws ThingsboardException {
        validateExpectedServiceId(expectedServiceId);
        return nodeDrainService.cancelDrain();
    }

    // ThingsboardException, because BaseController's handler turns any other exception into a 500.
    // The parameter is optional in the binding so that a missing value is rejected here with the same 400.
    private void validateExpectedServiceId(String expectedServiceId) throws ThingsboardException {
        if (StringUtils.isBlank(expectedServiceId)) {
            throw new ThingsboardException("Parameter 'expectedServiceId' is required", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        String actualServiceId = serviceInfoProvider.getServiceId();
        if (!actualServiceId.equals(expectedServiceId)) {
            throw new ThingsboardException("Drain request targets service '" + expectedServiceId +
                    "', but reached service '" + actualServiceId + "'", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

}
