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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.service.IntegrationManagerService;
import org.thingsboard.mqtt.broker.service.integration.PlatformIntegrationService;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.thingsboard.mqtt.broker.controller.ControllerConstants.INTEGRATION_ID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IntegrationController extends BaseController {

    private final IntegrationManagerService integrationManagerService;
    private final PlatformIntegrationService platformIntegrationService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/integration/{integrationId}")
    public Integration getIntegrationById(@PathVariable(INTEGRATION_ID) String strIntegrationId) throws Exception {
        checkParameter(INTEGRATION_ID, strIntegrationId);
        UUID integrationId = toUUID(strIntegrationId);
        return checkNotNull(integrationService.findIntegrationById(integrationId));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/integration")
    public Integration saveIntegration(@RequestBody Integration integration) throws Exception {
        try {
            integrationManagerService.validateIntegrationConfiguration(integration).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throwRealCause(e);
        } catch (TimeoutException e) {
            throw new ThingsboardRuntimeException("Timeout to validate the configuration!", ThingsboardErrorCode.GENERAL);
        }

        boolean created = integration.getId() == null;
        if (created && !rateLimitService.checkIntegrationsLimit()) {
            throw new ThingsboardException("Integrations limit exceeded", ThingsboardErrorCode.GENERAL);
        }

        Integration result = checkNotNull(integrationService.saveIntegration(integration));
        platformIntegrationService.processIntegrationUpdate(result, created);
        return result;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/integrations", params = {"pageSize", "page"})
    public PageData<Integration> getIntegrations(
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder) throws Exception {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(integrationService.findIntegrations(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/integration/check")
    public void checkIntegrationConnection(@RequestBody Integration integration) throws Exception {
        try {
            checkNotNull(integration);
            try {
                integrationManagerService.checkIntegrationConnection(integration).get(
                        integrationManagerService.getIntegrationConnectionCheckApiRequestTimeoutSec(), TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throwRealCause(e);
            }
        } catch (TimeoutException e) {
            throw new ThingsboardRuntimeException("Timeout to process the request!", ThingsboardErrorCode.GENERAL);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/integration/{integrationId}")
    @ResponseStatus(HttpStatus.OK)
    public void restartIntegration(@PathVariable(INTEGRATION_ID) String strIntegrationId) throws Exception {
        checkParameter(INTEGRATION_ID, strIntegrationId);
        Integration integration = checkIntegrationId(toUUID(strIntegrationId));
        platformIntegrationService.processIntegrationRestart(integration);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/integration/{integrationId}")
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteIntegration(@PathVariable(INTEGRATION_ID) String strIntegrationId) throws Exception {
        checkParameter(INTEGRATION_ID, strIntegrationId);
        Integration integration = checkIntegrationId(toUUID(strIntegrationId));
        boolean removed = integrationService.deleteIntegration(integration);
        if (removed) {
            rateLimitCacheService.decrementApplicationClientsCount();
        }
        platformIntegrationService.processIntegrationDelete(integration, removed);
    }

}
