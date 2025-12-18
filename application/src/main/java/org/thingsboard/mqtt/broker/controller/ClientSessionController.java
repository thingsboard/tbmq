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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.cache.TbCacheOps.Status;
import org.thingsboard.mqtt.broker.common.data.ClientSessionQuery;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dto.ClientSessionAdvancedDto;
import org.thingsboard.mqtt.broker.dto.ClientSessionStatsInfoDto;
import org.thingsboard.mqtt.broker.dto.DetailedClientSessionInfoDto;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPageInfos;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.SessionSubscriptionService;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClientSessionController extends BaseController {

    private final SessionSubscriptionService sessionSubscriptionService;
    private final ClientSessionPageInfos clientSessionPageInfos;
    private final TbCacheOps cacheOps;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/client-session/remove", params = {"clientId", "sessionId"})
    public void removeClientSession(@RequestParam String clientId,
                                    @RequestParam("sessionId") String sessionIdStr) throws ThingsboardException {
        checkParameter("clientId", clientId);
        checkParameter("sessionId", sessionIdStr);
        clientSessionCleanUpService.removeClientSession(clientId, toUUID(sessionIdStr));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/client-session/disconnect", params = {"clientId", "sessionId"})
    public void disconnectClientSession(@RequestParam String clientId,
                                        @RequestParam("sessionId") String sessionIdStr) throws ThingsboardException {
        checkParameter("clientId", clientId);
        checkParameter("sessionId", sessionIdStr);
        clientSessionCleanUpService.disconnectClientSession(clientId, toUUID(sessionIdStr));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/client-session", params = {"clientId"})
    public DetailedClientSessionInfoDto getDetailedClientSessionInfo(@RequestParam String clientId) throws ThingsboardException {
        checkParameter("clientId", clientId);
        return checkNotNull(sessionSubscriptionService.getDetailedClientSessionInfo(clientId));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/client-session", params = {"pageSize", "page"})
    public PageData<ShortClientSessionInfoDto> getShortClientSessionInfos(@RequestParam int pageSize,
                                                                          @RequestParam int page,
                                                                          @RequestParam(required = false) String textSearch,
                                                                          @RequestParam(required = false) String sortProperty,
                                                                          @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(clientSessionPageInfos.getClientSessionInfos(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/v2/client-session", params = {"pageSize", "page"})
    public PageData<ShortClientSessionInfoDto> getShortClientSessionInfosV2(@RequestParam int pageSize,
                                                                            @RequestParam int page,
                                                                            @RequestParam(required = false) String textSearch,
                                                                            @RequestParam(required = false) String sortProperty,
                                                                            @RequestParam(required = false) String sortOrder,
                                                                            @RequestParam(required = false) Long startTime,
                                                                            @RequestParam(required = false) Long endTime,
                                                                            @RequestParam(required = false) String[] connectedStatusList,
                                                                            @RequestParam(required = false) String[] clientTypeList,
                                                                            @RequestParam(required = false) String[] cleanStartList,
                                                                            @RequestParam(required = false) String[] nodeIdList,
                                                                            @RequestParam(required = false) Integer subscriptions,
                                                                            @RequestParam(required = false) String subscriptionOperation,
                                                                            @RequestParam(required = false) String clientIpAddress) throws ThingsboardException {
        List<ConnectionState> connectedStatuses = parseEnumList(ConnectionState.class, connectedStatusList);
        List<ClientType> clientTypes = parseEnumList(ClientType.class, clientTypeList);
        List<Boolean> cleanStarts = collectBooleanQueryParams(cleanStartList);

        Set<String> brokerNodeIdSet = nodeIdList != null ? Set.of(nodeIdList) : Collections.emptySet();

        SubscriptionOperation operation = subscriptionOperation == null ? SubscriptionOperation.EQUAL : SubscriptionOperation.valueOf(subscriptionOperation);

        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);

        return checkNotNull(clientSessionPageInfos.getClientSessionInfos(
                new ClientSessionQuery(pageLink, connectedStatuses, clientTypes, cleanStarts,
                        brokerNodeIdSet, subscriptions, operation, clientIpAddress)
        ));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/client-session/info")
    public ClientSessionStatsInfoDto getClientSessionsStatsInfo() throws ThingsboardException {
        return checkNotNull(clientSessionPageInfos.getClientSessionStatsInfo());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/client-session/details", params = {"clientId"})
    public ClientSessionAdvancedDto getClientSessionDetails(@RequestParam String clientId) throws ThingsboardException {
        checkParameter("clientId", clientId);
        String credentialsName = getValueFromCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE, clientId);
        String mqttVersion = getValueFromCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE, clientId);
        return new ClientSessionAdvancedDto(credentialsName, mqttVersion);
    }

    private String getValueFromCache(String cache, String clientId) {
        var lookup = cacheOps.lookup(cache, clientId, String.class);
        return lookup.status() == Status.HIT ? lookup.value() : "Unknown";
    }
}
