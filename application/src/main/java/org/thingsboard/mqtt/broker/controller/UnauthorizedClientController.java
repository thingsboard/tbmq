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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/unauthorized/client")
public class UnauthorizedClientController extends BaseController {

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"pageSize", "page"})
    public PageData<UnauthorizedClient> getUnauthorizedClients(@RequestParam int pageSize,
                                                               @RequestParam int page,
                                                               @RequestParam(required = false) String textSearch,
                                                               @RequestParam(required = false) String sortProperty,
                                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(unauthorizedClientService.findUnauthorizedClients(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"clientId"})
    public UnauthorizedClient getUnauthorizedClient(@RequestParam String clientId) throws ThingsboardException {
        checkParameter("clientId", clientId);
        return checkNotNull(unauthorizedClientService.findUnauthorizedClient(clientId).orElse(null));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "", params = {"clientId"})
    public void deleteUnauthorizedClient(@RequestParam String clientId) throws ThingsboardException {
        UnauthorizedClient unauthorizedClient = checkUnauthorizedClient(clientId);
        unauthorizedClientService.deleteUnauthorizedClient(unauthorizedClient.getClientId());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "")
    public void deleteAllUnauthorizedClients() {
        unauthorizedClientService.deleteAllUnauthorizedClients();
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/v2", params = {"pageSize", "page"})
    public PageData<UnauthorizedClient> getCredentialsV2(@RequestParam int pageSize,
                                                         @RequestParam int page,
                                                         @RequestParam(required = false) String textSearch,
                                                         @RequestParam(required = false) String sortProperty,
                                                         @RequestParam(required = false) String sortOrder,
                                                         @RequestParam(required = false) Long startTime,
                                                         @RequestParam(required = false) Long endTime,
                                                         @RequestParam(required = false) String clientId,
                                                         @RequestParam(required = false) String ipAddress,
                                                         @RequestParam(required = false) String username,
                                                         @RequestParam(required = false) String reason,
                                                         @RequestParam(required = false) String[] passwordProvidedList,
                                                         @RequestParam(required = false) String[] tlsUsedList) throws ThingsboardException {
        List<Boolean> passwordProvidedValues = collectBooleanQueryParams(passwordProvidedList);
        List<Boolean> tlsUsedValues = collectBooleanQueryParams(tlsUsedList);

        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);

        return checkNotNull(unauthorizedClientService.findUnauthorizedClients(
                new UnauthorizedClientQuery(pageLink, clientId, ipAddress, username, reason, passwordProvidedValues, tlsUsedValues)
        ));
    }

}
