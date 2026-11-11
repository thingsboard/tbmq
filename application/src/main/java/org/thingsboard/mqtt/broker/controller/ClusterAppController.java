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

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaBroker;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.entity.app.TbAppService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventProcessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
@ClusterComponent
@Tag(name = "app-controller", description = "App Controller")
@Slf4j
public class ClusterAppController extends BaseController {

    private final ApplicationRemovedEventProcessor applicationRemovedEventProcessor;
    private final TbQueueAdmin tbQueueAdmin;
    private final TbAppService tbAppService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/remove-topics")
    public void removeApplicationTopics() {
        applicationRemovedEventProcessor.processEvents();
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/cluster-info")
    public PageData<KafkaBroker> getKafkaClusterInfo() throws ThingsboardException {
        return checkNotNull(tbQueueAdmin.getClusterInfo());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/cluster-info/v2")
    public CompletableFuture<PageData<KafkaBroker>> getKafkaClusterInfoV2() {
        return tbQueueAdmin.getClusterInfoAsync()
                .thenApply(res -> {
                    try {
                        return checkNotNull(res);
                    } catch (ThingsboardException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/kafka-topics", params = {"pageSize", "page"})
    public PageData<KafkaTopic> getKafkaTopics(@RequestParam int pageSize,
                                               @RequestParam int page,
                                               @RequestParam(required = false) String textSearch,
                                               @RequestParam(required = false) String sortProperty,
                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(tbQueueAdmin.getTopics(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/kafka-topics/v2", params = {"pageSize", "page"})
    public CompletableFuture<PageData<KafkaTopic>> getKafkaTopicsV2(@RequestParam int pageSize,
                                                                    @RequestParam int page,
                                                                    @RequestParam(required = false) String textSearch,
                                                                    @RequestParam(required = false) String sortProperty,
                                                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return tbQueueAdmin.getTopicsAsync(pageLink)
                .thenApply(res -> {
                    try {
                        return checkNotNull(res);
                    } catch (ThingsboardException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/consumer-groups", params = {"pageSize", "page"})
    public PageData<KafkaConsumerGroup> getKafkaConsumerGroups(@RequestParam int pageSize,
                                                               @RequestParam int page,
                                                               @RequestParam(required = false) String textSearch,
                                                               @RequestParam(required = false) String sortProperty,
                                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(tbQueueAdmin.getConsumerGroups(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/consumer-groups/v2", params = {"pageSize", "page"})
    public CompletableFuture<PageData<KafkaConsumerGroup>> getKafkaConsumerGroupsV2(@RequestParam int pageSize,
                                                                                    @RequestParam int page,
                                                                                    @RequestParam(required = false) String textSearch,
                                                                                    @RequestParam(required = false) String sortProperty,
                                                                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return tbQueueAdmin.getConsumerGroupsAsync(pageLink)
                .thenApply(res -> {
                    try {
                        return checkNotNull(res);
                    } catch (ThingsboardException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/consumer-group")
    @io.swagger.v3.oas.annotations.Operation(hidden = true)
    public void deleteKafkaConsumerGroup(@RequestParam String groupId) throws Exception {
        checkParameter("groupId", groupId);
        tbAppService.deleteKafkaConsumerGroup(groupId, getCurrentUser());
    }
}
