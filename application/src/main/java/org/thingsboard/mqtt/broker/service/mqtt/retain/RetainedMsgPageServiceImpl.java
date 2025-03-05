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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.mqtt.retained.RetainedMsgQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetainedMsgPageServiceImpl implements RetainedMsgPageService {

    private final RetainedMsgListenerService retainedMsgListenerService;

    @Override
    public PageData<RetainedMsgDto> getRetainedMessages(PageLink pageLink) {
        List<RetainedMsg> retainedMessages = retainedMsgListenerService.getRetainedMessages();

        List<RetainedMsg> filteredByTextSearch = filterRetainedMessages(retainedMessages, pageLink);

        return mapToPageDataResponse(filteredByTextSearch.stream().map(this::toRetainedMsgDto), pageLink, filteredByTextSearch.size());
    }

    @Override
    public PageData<RetainedMsgDto> getRetainedMessages(RetainedMsgQuery query) {
        List<RetainedMsg> retainedMessages = retainedMsgListenerService.getRetainedMessages();
        if (CollectionUtils.isEmpty(retainedMessages)) {
            return PageData.emptyPageData();
        }

        TimePageLink pageLink = query.getPageLink();
        Long startTime = pageLink.getStartTime();
        Long endTime = pageLink.getEndTime();
        String topicName = query.getTopicName();
        Set<Integer> qosSet = query.getQosSet();
        String payload = query.getPayload();

        List<RetainedMsgDto> filteredRetainedMessages = retainedMessages.parallelStream()
                .filter(retainedMsg -> filterByTopic(pageLink, retainedMsg))
                .filter(retainedMsg -> filterByTopic(topicName, retainedMsg))
                .filter(retainedMsg -> filterByQos(qosSet, retainedMsg))
                .filter(retainedMsg -> filterByTimeRange(startTime, endTime, retainedMsg))
                .map(this::toRetainedMsgDto)
                .filter(retainedMsgDto -> filterByPayload(payload, retainedMsgDto))
                .toList();

        return mapToPageDataResponse(filteredRetainedMessages.stream(), pageLink, filteredRetainedMessages.size());
    }

    private PageData<RetainedMsgDto> mapToPageDataResponse(Stream<RetainedMsgDto> stream, PageLink pageLink, int totalSize) {
        List<RetainedMsgDto> data = stream
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        return PageData.of(data, totalSize, pageLink);
    }

    private RetainedMsgDto toRetainedMsgDto(RetainedMsg retainedMsg) {
        return RetainedMsgDto.newInstance(retainedMsg);
    }

    private Comparator<? super RetainedMsgDto> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, RetainedMsgDto::getComparator);
    }

    private List<RetainedMsg> filterRetainedMessages(List<RetainedMsg> retainedMessages, PageLink pageLink) {
        return retainedMessages.stream()
                .filter(retainedMsg -> filterByTopic(pageLink, retainedMsg))
                .collect(Collectors.toList());
    }

    private boolean filterByTopic(PageLink pageLink, RetainedMsg retainedMsg) {
        return doFilterByTopic(pageLink.getTextSearch(), retainedMsg);
    }

    private boolean filterByTopic(String topicName, RetainedMsg retainedMsg) {
        return doFilterByTopic(topicName, retainedMsg);
    }

    private boolean doFilterByTopic(String topicName, RetainedMsg retainedMsg) {
        if (topicName != null) {
            return retainedMsg.getTopic().toLowerCase().contains(topicName.toLowerCase());
        }
        return true;
    }

    private boolean filterByQos(Set<Integer> qosSet, RetainedMsg retainedMsg) {
        return CollectionUtils.isEmpty(qosSet) || qosSet.contains(retainedMsg.getQos());
    }

    private boolean filterByTimeRange(Long startTime, Long endTime, RetainedMsg retainedMsg) {
        if (startTime != null && endTime != null) {
            return isInTimeRange(retainedMsg.getCreatedTime(), startTime, endTime);
        }
        return true;
    }

    private static boolean isInTimeRange(long ts, long startTime, long endTime) {
        return ts >= startTime && ts <= endTime;
    }

    private boolean filterByPayload(String payload, RetainedMsgDto retainedMsgDto) {
        if (payload != null) {
            return retainedMsgDto.getPayload().toLowerCase().contains(payload.toLowerCase());
        }
        return true;
    }
}
