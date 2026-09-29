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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.AuthorizationPolicy;
import org.thingsboard.mqtt.broker.dao.auth.AuthorizationPolicyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/authorization/policies")
public class AuthorizationPolicyController extends BaseController {

    private final AuthorizationPolicyService authorizationPolicyService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public AuthorizationPolicy savePolicy(@RequestBody AuthorizationPolicy policy) {
        return authorizationPolicyService.savePolicy(policy);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(params = {"pageSize", "page"})
    public PageData<AuthorizationPolicy> getPolicies(@RequestParam int pageSize,
                                                     @RequestParam int page,
                                                     @RequestParam(required = false) String textSearch,
                                                     @RequestParam(required = false) String sortProperty,
                                                     @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return authorizationPolicyService.getPolicies(pageLink);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/{id}")
    public AuthorizationPolicy getPolicy(@PathVariable String id) throws ThingsboardException {
        return checkNotNull(authorizationPolicyService.getPolicyById(toUUID(id)).orElse(null));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping("/{id}")
    public void deletePolicy(@PathVariable String id) throws ThingsboardException {
        authorizationPolicyService.deletePolicy(toUUID(id));
    }
}
