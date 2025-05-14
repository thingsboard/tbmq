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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderDto;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MqttClientAuthProviderController extends BaseController {

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/auth/provider", method = RequestMethod.POST)
    @ResponseBody
    public MqttAuthProviderDto saveAuthProvider(@RequestBody MqttAuthProviderDto authProvider) throws ThingsboardException {
        checkNotNull(authProvider);
        try {
            return checkNotNull(mqttClientAuthProviderManagerService.saveAuthProvider(authProvider));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/auth/provider/{authProviderId}/enable", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    public void enableAuthProvider(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        try {
            UUID uuid = toUUID(strAuthProviderId);
            checkAuthProviderId(uuid);
            mqttClientAuthProviderManagerService.enableAuthProvider(uuid);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/auth/provider/{authProviderId}/disable", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    public void disableAuthProvider(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        try {
            UUID uuid = toUUID(strAuthProviderId);
            checkAuthProviderId(uuid);
            mqttClientAuthProviderManagerService.disableAuthProvider(uuid);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/auth/providers", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ShortMqttClientAuthProvider> getAuthProviders(@RequestParam int pageSize,
                                                                  @RequestParam int page,
                                                                  @RequestParam(required = false) String textSearch,
                                                                  @RequestParam(required = false) String sortProperty,
                                                                  @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(mqttClientAuthProviderService.getAuthProviders(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/auth/provider/{authProviderId}", method = RequestMethod.GET)
    public MqttAuthProviderDto getAuthProviderById(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        try {
            return checkNotNull(mqttClientAuthProviderService.getAuthProviderById(toUUID(strAuthProviderId)).orElse(null));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/auth/provider/{authProviderId}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    public void deleteAuthProvider(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        try {
            UUID uuid = toUUID(strAuthProviderId);
            checkAuthProviderId(uuid);
            mqttClientAuthProviderManagerService.deleteAuthProvider(uuid);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
