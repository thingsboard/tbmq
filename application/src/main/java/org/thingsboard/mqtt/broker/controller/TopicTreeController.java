/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.permission.Operation;
import org.thingsboard.mqtt.broker.dto.DroppedMsgDto;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeNodeDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeSubscriptionDto;
import org.thingsboard.mqtt.broker.service.mqtt.dropped.DroppedMsgPageService;
import org.thingsboard.mqtt.broker.service.topictree.TopicResolveService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeColumn;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeNode;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeQuery;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSnapshot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topic-tree")
public class TopicTreeController extends BaseController {

    private static final int MAX_DROPPED_PAGE_SIZE = 100;

    private final TopicTreeService topicTreeService;
    private final TopicResolveService topicResolveService;
    private final DroppedMsgPageService droppedMsgPageService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/children")
    public PageData<TopicTreeNodeDto> getChildren(@RequestParam(required = false) String path,
                                                  @RequestParam(required = false) String columns,
                                                  @RequestParam(defaultValue = "100") int pageSize,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(required = false) String sortProperty,
                                                  @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        return doChildren(path, columns, pageSize, page, sortProperty, sortOrder);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/search")
    public PageData<TopicTreeNodeDto> search(@RequestParam(required = false) String filter,
                                             @RequestParam(required = false) String topic,
                                             @RequestParam(required = false) String topicMatch,
                                             @RequestParam(required = false) String matchesTopic,
                                             @RequestParam(required = false) String has,
                                             @RequestParam(required = false) String clientStatus,
                                             @RequestParam(required = false) String clientType,
                                             @RequestParam(defaultValue = "false") boolean wildcardOnly,
                                             @RequestParam(required = false) Integer subscriptions,
                                             @RequestParam(required = false) String subscriptionOperation,
                                             @RequestParam(required = false) String columns,
                                             @RequestParam(defaultValue = "100") int pageSize,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(required = false) String sortProperty,
                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        if (filter != null && !filter.isEmpty()) {
            checkFilter(filter);
        }
        TopicTreeQuery query = new TopicTreeQuery(
                filter == null || filter.isEmpty() ? null : filter,
                topic,
                "PREFIX".equalsIgnoreCase(topicMatch),
                matchesTopic == null || matchesTopic.isEmpty() ? null : matchingPaths(matchesTopic),
                parseEnums(has, TopicTreeQuery.Has.class),
                parseEnums(clientStatus, TopicTreeQuery.ClientStatus.class),
                parseEnums(clientType, ClientType.class),
                wildcardOnly,
                subscriptions,
                subscriptionOperation == null ? null : parseEnum(subscriptionOperation, SubscriptionOperation.class));
        return doQuery(query, columns, pageSize, page, sortProperty, sortOrder);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/node")
    public TopicTreeNodeDto getNode(@RequestParam(required = false) String path,
                                    @RequestParam(required = false) String columns) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseColumns(columns);
        TopicTreeNode node = node(s, path);
        TopicTreeNodeDto dto = withDropped(List.of(TopicTreeNodeDto.of(node, s, cols)), cols).get(0);
        if (cols.contains(TopicTreeColumn.SHARED)) {
            dto.setSharedSubscriptionsTotal(s.sharedSubscriptions(node));
        }
        return dto;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/entities")
    public PageData<TopicTreeEntityDto> getEntities(@RequestParam(required = false) String path,
                                                    @RequestParam String kind,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        TopicTreeSnapshot s = snapshot();
        TopicTreeEntityDto.Kind k;
        try {
            k = TopicTreeEntityDto.Kind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException("Unknown kind: " + kind, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        PageData<TopicTreeEntityDto> entities = s.entities(node(s, path), k, new PageLink(clamp(pageSize), page));
        if (k == TopicTreeEntityDto.Kind.INTEGRATIONS) {
            entities.getData().forEach(e -> e.setName(integrationName(e.getId())));
        }
        return entities;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/subscriptions")
    public PageData<TopicTreeSubscriptionDto> getSubscriptions(@RequestParam(required = false) String path,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        TopicTreeSnapshot s = snapshot();
        PageData<TopicTreeSubscriptionDto> subscriptions = s.subscriptions(node(s, path), new PageLink(clamp(pageSize), page));
        subscriptions.getData().stream().filter(TopicTreeSubscriptionDto::isIntegration)
                .forEach(sub -> sub.setIntegrationName(integrationName(sub.getClientId())));
        return subscriptions;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/retained")
    public PageData<RetainedMsgDto> getRetained(@RequestParam(required = false) String path,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        snapshotEnabledCheck();
        return topicTreeService.getRetainedMessages(path, new PageLink(clamp(pageSize), page));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/dropped")
    public PageData<DroppedMsgDto> getDropped(@RequestParam(required = false) String path,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(defaultValue = "0") int page) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        snapshotEnabledCheck();
        return droppedMsgPageService.getTopicSubtreeSince(path, droppedSince(), new PageLink(clamp(pageSize), page));
    }

    // Integration subscriptions are keyed by the integration id; stale ones may outlive the integration itself.
    private String integrationName(String id) {
        try {
            Integration integration = integrationService.findIntegrationById(UUID.fromString(id));
            return integration == null ? null : integration.getName();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    PageData<TopicTreeNodeDto> doChildren(String path, String columns, int pageSize, int page,
                                          String sortProperty, String sortOrder) throws ThingsboardException {
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseColumns(columns);
        PageLink link = createPageLink(clamp(pageSize, cols), page, null, sortProperty, sortOrder);
        PageData<TopicTreeNode> nodes = s.children(node(s, path), link, droppedFor("dropped".equals(sortProperty)));
        List<TopicTreeNodeDto> dtos = withDropped(nodes.getData().stream().map(n -> TopicTreeNodeDto.of(n, s, cols)).toList(), cols);
        return new PageData<>(dtos, nodes.getTotalPages(), nodes.getTotalElements(), nodes.hasNext());
    }

    PageData<TopicTreeNodeDto> doSearch(String filter, String columns, int pageSize, int page,
                                        String sortProperty, String sortOrder) throws ThingsboardException {
        checkFilter(filter);
        return doQuery(TopicTreeQuery.text(filter), columns, pageSize, page, sortProperty, sortOrder);
    }

    PageData<TopicTreeNodeDto> doQuery(TopicTreeQuery query, String columns, int pageSize, int page,
                                       String sortProperty, String sortOrder) throws ThingsboardException {
        if (query.isEmpty()) {
            throw new ThingsboardException("At least one filter criterion is required", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseColumns(columns);
        PageLink link = createPageLink(clamp(pageSize, cols), page, null, sortProperty, sortOrder);
        boolean needsDropped = query.has().contains(TopicTreeQuery.Has.DROPPED) || "dropped".equals(sortProperty);
        PageData<TopicTreeNode> nodes = s.query(query, droppedFor(needsDropped), link);
        List<TopicTreeNodeDto> dtos = withDropped(nodes.getData().stream().map(n -> TopicTreeNodeDto.of(n, s, cols)).toList(), cols);
        return new PageData<>(dtos, nodes.getTotalPages(), nodes.getTotalElements(), nodes.hasNext());
    }

    // Drops are stored per concrete topic, so each one is credited to every topic tree level above it.
    private ToLongFunction<TopicTreeNode> droppedFor(boolean needed) {
        if (!needed) {
            return n -> 0L;
        }
        long since = droppedSince();
        Map<String, Long> byPrefix = new HashMap<>();
        droppedMsgPageService.sumDroppedByTopicSince(since).forEach((topic, count) -> {
            for (int i = topic.indexOf('/'); i >= 0; i = topic.indexOf('/', i + 1)) {
                byPrefix.merge(topic.substring(0, i), count, Long::sum);
            }
            byPrefix.merge(topic, count, Long::sum);
        });
        return n -> n.path() == null ? 0L : byPrefix.getOrDefault(n.path(), 0L);
    }

    private static long droppedSince() {
        return System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
    }

    private Set<String> matchingPaths(String topic) throws ThingsboardException {
        try {
            Set<String> paths = new HashSet<>();
            topicResolveService.resolve(topic).getMatches().forEach(m -> paths.add(m.getTopicFilter()));
            return paths;
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private static <E extends Enum<E>> Set<E> parseEnums(String csv, Class<E> type) throws ThingsboardException {
        Set<E> result = EnumSet.noneOf(type);
        if (csv != null) {
            for (String value : csv.split(",")) {
                if (!value.isBlank()) {
                    result.add(parseEnum(value.trim(), type));
                }
            }
        }
        return result;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) throws ThingsboardException {
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException("Unknown " + type.getSimpleName() + ": " + value, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/resolve")
    public TopicResolveDto resolve(@RequestParam String topic) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        snapshotEnabledCheck();
        try {
            return topicResolveService.resolve(topic);
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
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

    private void checkFilter(String filter) throws ThingsboardException {
        if (filter == null || filter.length() < topicTreeService.getFilterMinLength()) {
            throw new ThingsboardException("Filter must have at least " + topicTreeService.getFilterMinLength() + " characters",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private Set<TopicTreeColumn> parseColumns(String columns) throws ThingsboardException {
        try {
            return TopicTreeColumn.parse(columns);
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException("Unknown column in: " + columns, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, topicTreeService.getMaxPageSize()));
    }

    private int clamp(int size, Set<TopicTreeColumn> cols) {
        int clamped = clamp(size);
        return cols.contains(TopicTreeColumn.DROPPED) ? Math.min(clamped, MAX_DROPPED_PAGE_SIZE) : clamped;
    }

    private List<TopicTreeNodeDto> withDropped(List<TopicTreeNodeDto> dtos, Set<TopicTreeColumn> cols) {
        List<TopicTreeNodeDto> result = new ArrayList<>(dtos);
        if (cols.contains(TopicTreeColumn.DROPPED)) {
            long since = droppedSince();
            List<String> paths = result.stream().map(TopicTreeNodeDto::getPath).filter(Objects::nonNull).toList();
            Map<String, Long> dropped = droppedMsgPageService.sumDroppedByTopicPrefixesSince(paths, since);
            result.forEach(dto -> dto.setDropped24h(dto.getPath() == null ? null : dropped.getOrDefault(dto.getPath(), 0L)));
        }
        return result;
    }
}
