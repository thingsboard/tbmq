/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgPageService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/retained-msg")
public class RetainedMsgController extends BaseController {

    private final RetainedMsgService retainedMsgService;
    private final RetainedMsgPageService retainedMsgPageService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/topic-trie/clear", method = RequestMethod.DELETE)
    @ResponseBody
    public void clearEmptyRetainedMsgNodes() throws ThingsboardException {
        try {
            retainedMsgService.clearEmptyTopicNodes();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"topicName"}, method = RequestMethod.GET)
    @ResponseBody
    public RetainedMsgDto getRetainedMessage(@RequestParam String topicName) throws ThingsboardException {
        try {
            return checkNotNull(retainedMsgListenerService.getRetainedMsgForTopic(topicName));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"topicName"}, method = RequestMethod.DELETE)
    @ResponseBody
    public void deleteRetainedMessage(@RequestParam String topicName) throws ThingsboardException {
        try {
            checkRetainedMsg(topicName);
            retainedMsgListenerService.clearRetainedMsgAndPersist(topicName);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<RetainedMsgDto> getRetainedMessages(@RequestParam int pageSize,
                                                        @RequestParam int page,
                                                        @RequestParam(required = false) String textSearch,
                                                        @RequestParam(required = false) String sortProperty,
                                                        @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(retainedMsgPageService.getRetainedMessages(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
