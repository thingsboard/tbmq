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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration.AuthStrategy;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.service.auth.providers.http.HttpMqttClientAuthProvider;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MqttAuthProviderController extends BaseController {

    private final HttpMqttClientAuthProvider httpMqttClientAuthProvider;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/mqtt/auth/provider")
    public MqttAuthProvider saveAuthProvider(@Valid @RequestBody MqttAuthProvider authProvider) throws ThingsboardException {
        return checkNotNull(mqttAuthProviderManagerService.saveAuthProvider(authProvider));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/mqtt/auth/provider/{authProviderId}/enable")
    @ResponseStatus(HttpStatus.OK)
    public void enableAuthProvider(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        UUID uuid = toUUID(strAuthProviderId);
        checkAuthProviderId(uuid);
        mqttAuthProviderManagerService.enableAuthProvider(uuid);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/mqtt/auth/provider/{authProviderId}/disable")
    @ResponseStatus(HttpStatus.OK)
    public void disableAuthProvider(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        UUID uuid = toUUID(strAuthProviderId);
        checkAuthProviderId(uuid);
        mqttAuthProviderManagerService.disableAuthProvider(uuid);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/auth/providers", params = {"pageSize", "page"})
    public PageData<ShortMqttAuthProvider> getAuthProviders(@RequestParam int pageSize,
                                                            @RequestParam int page,
                                                            @RequestParam(required = false) String textSearch,
                                                            @RequestParam(required = false) String sortProperty,
                                                            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(mqttAuthProviderService.getShortAuthProviders(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/auth/provider/{authProviderId}")
    public MqttAuthProvider getAuthProviderById(@PathVariable("authProviderId") String strAuthProviderId) throws ThingsboardException {
        return checkNotNull(mqttAuthProviderService.getAuthProviderById(toUUID(strAuthProviderId)).orElse(null));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/auth/provider/type/{authProviderType}")
    public MqttAuthProvider getAuthProviderByType(@PathVariable("authProviderType") String authProviderType) throws ThingsboardException {
        return checkNotNull(mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.valueOf(authProviderType)));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/auth/provider/basic/strategy")
    public AuthStrategy getBasicAuthProviderStrategy() {
        return mqttAuthProviderService.getMqttBasicProviderAuthStrategy();
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/mqtt/auth/provider/http/check")
    public void checkHttpAuthProviderConnection(@RequestBody MqttAuthProvider authProvider) throws Exception {
        checkNotNull(authProvider);
        if (authProvider.getType() != MqttAuthProviderType.HTTP) {
            throw new ThingsboardException("Auth provider type is not HTTP!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        try {
            try {
                httpMqttClientAuthProvider.checkConnection(authProvider).get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throwRealCause(e);
            }
        } catch (TimeoutException e) {
            throw new ThingsboardRuntimeException("Timeout to process the request!", ThingsboardErrorCode.GENERAL);
        }
    }

}
