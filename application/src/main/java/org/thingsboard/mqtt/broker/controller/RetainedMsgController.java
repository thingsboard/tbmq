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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.mqtt.retained.RetainedMsgQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.exception.RetainMsgTrieClearException;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgPageService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/retained-msg")
public class RetainedMsgController extends BaseController {

    private final RetainedMsgService retainedMsgService;
    private final RetainedMsgPageService retainedMsgPageService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/topic-trie/clear")
    public void clearEmptyRetainedMsgNodes() {
        try {
            retainedMsgService.clearEmptyTopicNodes();
        } catch (RetainMsgTrieClearException e) {
            throw new ThingsboardRuntimeException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"topicName"})
    public RetainedMsgDto getRetainedMessage(@RequestParam String topicName) throws ThingsboardException {
        checkParameter("topicName", topicName);
        return checkNotNull(retainedMsgListenerService.getRetainedMsgForTopic(topicName));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "", params = {"topicName"})
    public void deleteRetainedMessage(@RequestParam String topicName) throws ThingsboardException {
        checkRetainedMsg(topicName);
        retainedMsgListenerService.clearRetainedMsgAndPersist(topicName);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"pageSize", "page"})
    public PageData<RetainedMsgDto> getRetainedMessages(@RequestParam int pageSize,
                                                        @RequestParam int page,
                                                        @RequestParam(required = false) String textSearch,
                                                        @RequestParam(required = false) String sortProperty,
                                                        @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(retainedMsgPageService.getRetainedMessages(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/v2", params = {"pageSize", "page"})
    public PageData<RetainedMsgDto> getRetainedMessagesV2(@RequestParam int pageSize,
                                                          @RequestParam int page,
                                                          @RequestParam(required = false) String textSearch,
                                                          @RequestParam(required = false) String sortProperty,
                                                          @RequestParam(required = false) String sortOrder,
                                                          @RequestParam(required = false) Long startTime,
                                                          @RequestParam(required = false) Long endTime,
                                                          @RequestParam(required = false) String topicName,
                                                          @RequestParam(required = false) String[] qosList,
                                                          @RequestParam(required = false) String payload) throws ThingsboardException {
        Set<Integer> allQos = collectIntegerQueryParams(qosList);

        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);

        return checkNotNull(retainedMsgPageService.getRetainedMessages(
                new RetainedMsgQuery(pageLink, topicName, allQos, payload)
        ));
    }
}
