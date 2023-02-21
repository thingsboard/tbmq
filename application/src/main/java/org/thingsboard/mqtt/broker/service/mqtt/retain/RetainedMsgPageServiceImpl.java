package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
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
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .map(this::toRetainedMsgDto)
                .sorted(sorted(pageLink))
                .collect(Collectors.toList());

        return new PageData<>(data,
                filteredByTextSearch.size() / pageLink.getPageSize(),
                filteredByTextSearch.size(),
                pageLink.getPageSize() + pageLink.getPage() * pageLink.getPageSize() < filteredByTextSearch.size());
    }

    private RetainedMsgDto toRetainedMsgDto(RetainedMsg retainedMsg) {
        return RetainedMsgDto.newInstance(retainedMsg);
    }

    private Comparator<? super RetainedMsgDto> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(RetainedMsgDto.getComparator(pageLink.getSortOrder()));
    }

    private List<RetainedMsg> filterRetainedMessages(List<RetainedMsg> retainedMessages, PageLink pageLink) {
        return retainedMessages.stream()
                .filter(retainedMsg -> filter(pageLink, retainedMsg))
                .collect(Collectors.toList());
    }

    private boolean filter(PageLink pageLink, RetainedMsg retainedMsg) {
        if (pageLink.getTextSearch() != null) {
            return retainedMsg.getTopic().contains(pageLink.getTextSearch());
        }
        return true;
    }
}
