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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeNodeDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeSubscriptionDto;
import org.thingsboard.mqtt.broker.service.topictree.TopicResolveService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeColumn;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeEntityKind;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeNode;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeQuery;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSnapshot;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSortProperty;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topic-tree")
public class TopicTreeController extends BaseController {

    static final int MAX_PAGE_SIZE = 100;

    private final TopicTreeService topicTreeService;
    private final TopicResolveService topicResolveService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/children")
    public PageData<TopicTreeNodeDto> getChildren(@RequestParam(required = false) String path,
                                                  @RequestParam(required = false) String[] columns,
                                                  @RequestParam(defaultValue = "100") int pageSize,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(required = false) String sortProperty,
                                                  @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseEnums("columns", columns, TopicTreeColumn.class);
        PageLink link = pageLink(pageSize, page, sortProperty, sortOrder);
        return s.children(node(s, path), link).mapData(n -> s.toDto(n, cols));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/search")
    public PageData<TopicTreeNodeDto> search(@RequestParam(required = false) String textSearch,
                                             @RequestParam(required = false) String topic,
                                             @RequestParam(required = false) String topicMatch,
                                             @RequestParam(required = false) String matchesTopic,
                                             @RequestParam(required = false) String[] has,
                                             @RequestParam(defaultValue = "false") boolean wildcardOnly,
                                             @RequestParam(required = false) Integer subscriptions,
                                             @RequestParam(required = false) String subscriptionOperation,
                                             @RequestParam(required = false) String[] columns,
                                             @RequestParam(defaultValue = "100") int pageSize,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(required = false) String sortProperty,
                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        TopicTreeQuery.TopicMatch match = topicMatch == null ? TopicTreeQuery.TopicMatch.CONTAINS
                : checkEnumParameter("topicMatch", topicMatch, TopicTreeQuery.TopicMatch::valueOf);
        TopicTreeQuery query = TopicTreeQuery.builder()
                .text(StringUtils.isEmpty(textSearch) ? null : textSearch)
                .topic(StringUtils.isEmpty(topic) ? null : topic)
                .topicMatch(match)
                .matchingPaths(StringUtils.isEmpty(matchesTopic) ? null : matchingPaths(matchesTopic))
                .has(parseEnums("has", has, TopicTreeQuery.Has.class))
                .wildcardOnly(wildcardOnly)
                .subscriptions(subscriptions)
                .subscriptionOperation(subscriptionOperation == null ? null
                        : checkEnumParameter("subscriptionOperation", subscriptionOperation, SubscriptionOperation::valueOf))
                .build();
        if (query.isEmpty()) {
            throw new ThingsboardException("At least one filter criterion is required", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseEnums("columns", columns, TopicTreeColumn.class);
        PageLink link = pageLink(pageSize, page, sortProperty, sortOrder);
        return s.query(query, link).mapData(n -> s.toDto(n, cols));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/node")
    public TopicTreeNodeDto getNode(@RequestParam(required = false) String path,
                                    @RequestParam(required = false) String[] columns) throws ThingsboardException {
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseEnums("columns", columns, TopicTreeColumn.class);
        return s.toDto(node(s, path), cols);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/entities")
    public PageData<TopicTreeEntityDto> getEntities(@RequestParam(required = false) String path,
                                                    @RequestParam String kind,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        requirePath(path);
        TopicTreeEntityKind entityKind = checkEnumParameter("kind", kind, TopicTreeEntityKind::valueOf);
        TopicTreeSnapshot s = snapshot();
        PageData<TopicTreeEntityDto> entities = s.entities(node(s, path), entityKind, new PageLink(clamp(pageSize), page));
        if (entityKind == TopicTreeEntityKind.INTEGRATIONS) {
            Map<String, String> names = integrationNames(entities.getData().stream().map(TopicTreeEntityDto::getId));
            entities.getData().forEach(e -> e.setName(names.get(e.getId())));
        }
        return entities;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/subscriptions")
    public PageData<TopicTreeSubscriptionDto> getSubscriptions(@RequestParam(required = false) String path,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        requirePath(path);
        TopicTreeSnapshot s = snapshot();
        PageData<TopicTreeSubscriptionDto> subscriptions = s.subscriptions(node(s, path), new PageLink(clamp(pageSize), page));
        Map<String, String> names = integrationNames(subscriptions.getData().stream()
                .filter(TopicTreeSubscriptionDto::isIntegration).map(TopicTreeSubscriptionDto::getClientId));
        subscriptions.getData().stream().filter(TopicTreeSubscriptionDto::isIntegration)
                .forEach(sub -> sub.setIntegrationName(names.get(sub.getClientId())));
        return subscriptions;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/retained")
    public PageData<RetainedMsgDto> getRetained(@RequestParam(required = false) String path,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        requirePath(path);
        snapshotEnabledCheck();
        return topicTreeService.getRetainedMessages(path, new PageLink(clamp(pageSize), page));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/resolve")
    public TopicResolveDto resolve(@RequestParam String topic) throws ThingsboardException {
        snapshotEnabledCheck();
        return topicResolveService.resolve(topic);
    }

    // Integration subscriptions are keyed by the integration id; stale ones may outlive the integration itself.
    // Each distinct id is looked up once per request.
    private Map<String, String> integrationNames(Stream<String> ids) {
        Map<String, String> names = new HashMap<>();
        ids.distinct().forEach(id -> names.put(id, integrationName(id)));
        return names;
    }

    private String integrationName(String id) {
        try {
            Integration integration = integrationService.findIntegrationById(UUID.fromString(id));
            return integration == null ? null : integration.getName();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Set<String> matchingPaths(String topic) {
        Set<String> paths = new HashSet<>();
        topicResolveService.resolve(topic).getMatches().forEach(m -> paths.add(m.getTopicFilter()));
        return paths;
    }

    private <E extends Enum<E>> Set<E> parseEnums(String name, String[] values, Class<E> type) throws ThingsboardException {
        Set<E> result = EnumSet.noneOf(type);
        if (values != null) {
            for (String value : values) {
                if (StringUtils.isNotBlank(value)) {
                    result.add(checkEnumParameter(name, value.trim(), v -> Enum.valueOf(type, v)));
                }
            }
        }
        return result;
    }

    private PageLink pageLink(int pageSize, int page, String sortProperty, String sortOrder) throws ThingsboardException {
        if (StringUtils.isNotEmpty(sortProperty)) {
            checkEnumParameter("sortProperty", sortProperty, TopicTreeSortProperty::valueOf);
        }
        return createPageLink(clamp(pageSize), page, null, sortProperty, sortOrder);
    }

    private void snapshotEnabledCheck() throws ThingsboardException {
        if (!topicTreeService.isEnabled()) {
            throw new ThingsboardException("Topic tree is disabled", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
    }

    private TopicTreeSnapshot snapshot() throws ThingsboardException {
        snapshotEnabledCheck();
        return topicTreeService.getSnapshot();
    }

    private TopicTreeNode node(TopicTreeSnapshot s, String path) throws ThingsboardException {
        TopicTreeNode n = s.find(path);
        if (n == null) {
            throw new ThingsboardException("Topic path not found: " + path, ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        return n;
    }

    // Listing the whole tree would materialise every subscription/client on each page request; the UI never asks for it.
    private static void requirePath(String path) throws ThingsboardException {
        if (path == null) {
            throw new ThingsboardException("Topic path is required", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
