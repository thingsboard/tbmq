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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.service.mqtt.client.cleanup.ClientSessionCleanUpService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPageReader;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionReader;

@RestController
@RequestMapping("/api/client-session")
@RequiredArgsConstructor
public class ClientSessionController extends BaseController {
    private final ClientSessionCleanUpService clientSessionCleanUpService;
    private final ClientSessionReader clientSessionReader;
    private final ClientSessionPageReader clientSessionPageReader;


    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "/{clientId}/clear", method = RequestMethod.DELETE)
    @ResponseBody
    public void clearClientSession(@PathVariable("clientId") String clientId) throws ThingsboardException {
        try {
            clientSessionCleanUpService.removeClientSession(clientId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "/{clientId}", method = RequestMethod.GET)
    @ResponseBody
    public ClientSessionInfo getClientSessionInfo(@PathVariable("clientId") String clientId) throws ThingsboardException {
        try {
            return clientSessionReader.getClientSessionInfo(clientId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ClientSessionInfo> getClientSessionInfos(@RequestParam int pageSize, @RequestParam int page) throws ThingsboardException {
        try {
            PageLink pageLink = new PageLink(pageSize, page);
            return checkNotNull(clientSessionPageReader.getClientSessionInfos(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
