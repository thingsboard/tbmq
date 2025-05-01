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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientAuthenticator;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthenticator;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MqttClientAuthenticatorController extends BaseController {

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "mqtt/authenticator", method = RequestMethod.POST)
    @ResponseBody
    public MqttClientAuthenticator saveAuthenticator(@RequestBody MqttClientAuthenticator authenticator) throws ThingsboardException {
        checkNotNull(authenticator);
        try {
            return checkNotNull(mqttClientAuthenticatorService.saveAuthenticator(authenticator));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/authenticator", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ShortMqttClientAuthenticator> getAuthenticators(@RequestParam int pageSize,
                                                                    @RequestParam int page,
                                                                    @RequestParam(required = false) String textSearch,
                                                                    @RequestParam(required = false) String sortProperty,
                                                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(mqttClientAuthenticatorService.getAuthenticators(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "mqtt/authenticator/{authenticatorId}", method = RequestMethod.GET)
    public MqttClientAuthenticator getAuthenticatorById(@PathVariable("authenticatorId") String strAuthenticatorId) throws ThingsboardException {
        try {
            return checkNotNull(mqttClientAuthenticatorService.getAuthenticatorById(toUUID(strAuthenticatorId)).orElse(null));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "mqtt/authenticator/{authenticatorId}", method = RequestMethod.DELETE)
    public void deleteAuthenticator(@PathVariable("authenticatorId") String strAuthenticatorId) throws ThingsboardException {
        try {
            UUID uuid = toUUID(strAuthenticatorId);
            checkClientAuthenticatorId(uuid);
            mqttClientAuthenticatorService.deleteAuthenticator(uuid);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
