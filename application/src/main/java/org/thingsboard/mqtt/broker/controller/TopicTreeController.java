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
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.permission.Operation;
import org.thingsboard.mqtt.broker.dto.TopicChartPointDto;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeNodeDto;
import org.thingsboard.mqtt.broker.service.topictree.TopicResolveService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeColumn;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeNode;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSnapshot;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topic-tree")
public class TopicTreeController extends BaseController {

    private final TopicTreeService topicTreeService;
    private final TopicResolveService topicResolveService;

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
    public PageData<TopicTreeNodeDto> search(@RequestParam String filter,
                                             @RequestParam(required = false) String columns,
                                             @RequestParam(defaultValue = "100") int pageSize,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(required = false) String sortProperty,
                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        return doSearch(filter, columns, pageSize, page, sortProperty, sortOrder);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/node")
    public TopicTreeNodeDto getNode(@RequestParam(required = false) String path,
                                    @RequestParam(required = false) String columns) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        TopicTreeSnapshot s = snapshot();
        return TopicTreeNodeDto.of(node(s, path), s, parseColumns(columns));
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
        return s.entities(node(s, path), k, new PageLink(clamp(pageSize), page));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/chart")
    public List<TopicChartPointDto> getChart(@RequestParam(required = false) String filter,
                                             @RequestParam(defaultValue = "subscriptions") String metric,
                                             @RequestParam(defaultValue = "25") int limit,
                                             @RequestParam(required = false) Integer depth) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Operation.READ);
        return doChart(filter, metric, limit, depth);
    }

    PageData<TopicTreeNodeDto> doChildren(String path, String columns, int pageSize, int page,
                                          String sortProperty, String sortOrder) throws ThingsboardException {
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseColumns(columns);
        PageLink link = createPageLink(clamp(pageSize), page, null, sortProperty, sortOrder);
        PageData<TopicTreeNode> nodes = s.children(node(s, path), link);
        return new PageData<>(nodes.getData().stream().map(n -> TopicTreeNodeDto.of(n, s, cols)).toList(),
                nodes.getTotalPages(), nodes.getTotalElements(), nodes.hasNext());
    }

    PageData<TopicTreeNodeDto> doSearch(String filter, String columns, int pageSize, int page,
                                        String sortProperty, String sortOrder) throws ThingsboardException {
        checkFilter(filter);
        TopicTreeSnapshot s = snapshot();
        Set<TopicTreeColumn> cols = parseColumns(columns);
        PageLink link = createPageLink(clamp(pageSize), page, null, sortProperty, sortOrder);
        PageData<TopicTreeNode> nodes = s.search(filter, link);
        return new PageData<>(nodes.getData().stream().map(n -> TopicTreeNodeDto.of(n, s, cols)).toList(),
                nodes.getTotalPages(), nodes.getTotalElements(), nodes.hasNext());
    }

    List<TopicChartPointDto> doChart(String filter, String metric, int limit, Integer depth) throws ThingsboardException {
        if (filter != null && !filter.isEmpty()) {
            checkFilter(filter);
        }
        TopicTreeSnapshot s = snapshot();
        boolean byClients = "clients".equalsIgnoreCase(metric);
        return s.top(filter, depth, byClients, clamp(limit)).stream()
                .map(n -> new TopicChartPointDto(n.path(), n.subscriptionsTotal(), s.clientCounts(n).total()))
                .toList();
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
}
