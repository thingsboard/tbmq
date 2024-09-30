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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetainedMsgPageServiceImpl implements RetainedMsgPageService {

    private final RetainedMsgListenerService retainedMsgListenerService;

    @Override
    public PageData<RetainedMsgDto> getRetainedMessages(PageLink pageLink) {
        List<RetainedMsg> retainedMessages = retainedMsgListenerService.getRetainedMessages();

        List<RetainedMsg> filteredByTextSearch = filterRetainedMessages(retainedMessages, pageLink);

        List<RetainedMsgDto> data = filteredByTextSearch.stream()
                .map(this::toRetainedMsgDto)
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        return PageData.of(data, filteredByTextSearch.size(), pageLink);
    }

    private RetainedMsgDto toRetainedMsgDto(RetainedMsg retainedMsg) {
        return RetainedMsgDto.newInstance(retainedMsg);
    }

    private Comparator<? super RetainedMsgDto> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, RetainedMsgDto::getComparator);
    }

    private List<RetainedMsg> filterRetainedMessages(List<RetainedMsg> retainedMessages, PageLink pageLink) {
        return retainedMessages.stream()
                .filter(retainedMsg -> filter(pageLink, retainedMsg))
                .collect(Collectors.toList());
    }

    private boolean filter(PageLink pageLink, RetainedMsg retainedMsg) {
        if (pageLink.getTextSearch() != null) {
            return retainedMsg.getTopic().toLowerCase().contains(pageLink.getTextSearch().toLowerCase());
        }
        return true;
    }
}
