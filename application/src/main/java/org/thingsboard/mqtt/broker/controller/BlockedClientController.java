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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.service.entity.blockedclient.TbBlockedClientService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.BlockedClientPageService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientQuery;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.util.BlockedClientKeyUtil;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/blockedClient")
public class BlockedClientController extends BaseController {

    private final BlockedClientPageService blockedClientPageService;
    private final TbBlockedClientService tbBlockedClientService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public BlockedClientDto saveBlockedClient(@Valid @RequestBody BlockedClient blockedClient) throws ThingsboardException {
        checkNotNull(blockedClient);
        return tbBlockedClientService.save(blockedClient, getCurrentUser());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "", params = {"value", "type"})
    public void deleteBlockedClient(@RequestParam BlockedClientType type,
                                    @RequestParam String value,
                                    @RequestParam(required = false) RegexMatchTarget regexMatchTarget) throws ThingsboardException {
        String key = BlockedClientKeyUtil.generateKey(type, value, regexMatchTarget);
        BlockedClient blockedClient = checkBlockedClient(type, key);
        tbBlockedClientService.delete(blockedClient, getCurrentUser());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/ttl")
    public int getBlockedClientsTimeToLive() throws ThingsboardException {
        return blockedClientService.getBlockedClientCleanupTtl();
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"pageSize", "page"})
    public PageData<BlockedClientDto> getBlockedClients(@RequestParam int pageSize,
                                                        @RequestParam int page,
                                                        @RequestParam(required = false) String textSearch,
                                                        @RequestParam(required = false) String sortProperty,
                                                        @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(blockedClientPageService.getBlockedClients(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/v2", params = {"pageSize", "page"})
    public PageData<BlockedClientDto> getBlockedClientsV2(@RequestParam int pageSize,
                                                          @RequestParam int page,
                                                          @RequestParam(required = false) String textSearch,
                                                          @RequestParam(required = false) String sortProperty,
                                                          @RequestParam(required = false) String sortOrder,
                                                          @RequestParam(required = false) Long startTime,
                                                          @RequestParam(required = false) Long endTime,
                                                          @RequestParam(required = false) String[] typeList,
                                                          @RequestParam(required = false) String value,
                                                          @RequestParam(required = false) String[] regexMatchTargetList) throws ThingsboardException {
        Set<BlockedClientType> types = parseEnumSet(BlockedClientType.class, typeList);
        Set<RegexMatchTarget> regexMatchTargets = parseEnumSet(RegexMatchTarget.class, regexMatchTargetList);

        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);
        return checkNotNull(blockedClientPageService.getBlockedClients(new BlockedClientQuery(pageLink, types, value, regexMatchTargets)));
    }
}
