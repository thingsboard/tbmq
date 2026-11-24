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

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.Set;

/**
 * The subscription endpoints that exist only in the cluster edition. It shares the path prefix and the OpenAPI tag of
 * {@link SubscriptionController}, so the published API is the same as when both lived in one controller.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription")
@ClusterComponent
@Tag(name = "subscription-controller", description = "Subscription Controller")
public class ClusterSubscriptionController extends BaseController {

    private final ClientSubscriptionCache clientSubscriptionCache;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "integration", params = {"integrationId"})
    public Set<String> getIntegrationSubscriptions(@RequestParam String integrationId) throws ThingsboardException {
        toUUID(integrationId); // check if integrationId is UUID
        return checkNotNull(clientSubscriptionCache.getIntegrationSubscriptions(integrationId));
    }
}
