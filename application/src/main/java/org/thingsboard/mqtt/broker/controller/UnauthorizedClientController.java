/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/unauthorized/client")
public class UnauthorizedClientController extends BaseController {

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<UnauthorizedClient> getUnauthorizedClients(@RequestParam int pageSize,
                                                               @RequestParam int page,
                                                               @RequestParam(required = false) String textSearch,
                                                               @RequestParam(required = false) String sortProperty,
                                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(unauthorizedClientService.findUnauthorizedClients(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"clientId"}, method = RequestMethod.GET)
    public UnauthorizedClient getUnauthorizedClient(@RequestParam String clientId) throws ThingsboardException {
        try {
            return checkNotNull(unauthorizedClientService.findUnauthorizedClient(clientId).orElse(null));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"clientId"}, method = RequestMethod.DELETE)
    public void deleteUnauthorizedClient(@RequestParam String clientId) throws ThingsboardException {
        try {
            UnauthorizedClient unauthorizedClient = checkUnauthorizedClient(clientId);
            unauthorizedClientService.deleteUnauthorizedClient(unauthorizedClient.getClientId());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public void deleteAllUnauthorizedClients() throws ThingsboardException {
        try {
            unauthorizedClientService.deleteAllUnauthorizedClients();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/v2", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
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
        try {
            List<Boolean> passwordProvidedValues = new ArrayList<>();
            if (passwordProvidedList != null) {
                for (String strPasswordProvided : passwordProvidedList) {
                    if (!StringUtils.isEmpty(strPasswordProvided)) {
                        passwordProvidedValues.add(Boolean.valueOf(strPasswordProvided));
                    }
                }
            }

            List<Boolean> tlsUsedValues = new ArrayList<>();
            if (tlsUsedList != null) {
                for (String strTlsUsed : tlsUsedList) {
                    if (!StringUtils.isEmpty(strTlsUsed)) {
                        tlsUsedValues.add(Boolean.valueOf(strTlsUsed));
                    }
                }
            }

            TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);

            return checkNotNull(unauthorizedClientService.findUnauthorizedClients(
                    new UnauthorizedClientQuery(pageLink, clientId, ipAddress, username, reason, passwordProvidedValues, tlsUsedValues)
            ));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
