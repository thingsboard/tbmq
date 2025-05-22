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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.BlockedClientPageService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientQuery;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.util.BlockedClientKeyUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/blockedClient")
public class BlockedClientController extends BaseController {

    private final BlockedClientPageService blockedClientPageService;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public BlockedClient saveBlockedClient(@RequestBody BlockedClient blockedClient) throws ThingsboardException {
        checkNotNull(blockedClient);
        return blockedClientService.addBlockedClientAndPersist(blockedClient);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"value", "type"}, method = RequestMethod.DELETE)
    @ResponseBody
    public void deleteBlockedClient(@RequestParam BlockedClientType type,
                                    @RequestParam String value,
                                    @RequestParam(required = false) RegexMatchTarget regexMatchTarget) throws ThingsboardException {
        String key = BlockedClientKeyUtil.generateKey(type, value, regexMatchTarget);
        BlockedClient blockedClient = checkBlockedClient(type, key);
        blockedClientService.removeBlockedClientAndPersist(blockedClient);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<BlockedClientDto> getBlockedClients(@RequestParam int pageSize,
                                                        @RequestParam int page,
                                                        @RequestParam(required = false) String textSearch,
                                                        @RequestParam(required = false) String sortProperty,
                                                        @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(blockedClientPageService.getBlockedClients(pageLink));
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/v2", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
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
        Set<BlockedClientType> types = new HashSet<>();
        if (typeList != null) {
            for (String strType : typeList) {
                if (!StringUtils.isEmpty(strType)) {
                    types.add(BlockedClientType.valueOf(strType));
                }
            }
        }
        List<RegexMatchTarget> regexMatchTargets = new ArrayList<>();
        if (regexMatchTargetList != null) {
            for (String strRegexMatchTarget : regexMatchTargetList) {
                if (!StringUtils.isEmpty(strRegexMatchTarget)) {
                    regexMatchTargets.add(RegexMatchTarget.valueOf(strRegexMatchTarget));
                }
            }
        }

        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);
        return checkNotNull(blockedClientPageService.getBlockedClients(new BlockedClientQuery(pageLink, types, value, regexMatchTargets)));
    }
}
