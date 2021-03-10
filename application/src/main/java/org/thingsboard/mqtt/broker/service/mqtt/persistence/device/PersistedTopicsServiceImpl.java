package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistedTopicsServiceImpl implements PersistedTopicsService {
    private final Set<String> topics = Sets.newConcurrentHashSet();

    private final DeviceMsgService deviceMsgService;

    // TODO: clear persisted topics from time to time

    @PostConstruct
    public void init() {
        List<String> distinctStoredTopics = deviceMsgService.getAllTopics();
        log.info("Loading {} persisted topics in memory.", distinctStoredTopics.size());
        topics.addAll(distinctStoredTopics);
    }

    @Override
    public void addTopic(String topic) {
        topics.add(topic);
    }

    // TODO: write tests for this
    @Override
    public Set<String> getMatchingTopics(String topicFilter) {
        if (topicFilter.contains(BrokerConstants.MULTI_LEVEL_WILDCARD) || topicFilter.contains(BrokerConstants.SINGLE_LEVEL_WILDCARD)) {
            String topicFilterRegEx = topicFilter.replace(BrokerConstants.SINGLE_LEVEL_WILDCARD, "[^/]+")
                    .replace(BrokerConstants.MULTI_LEVEL_WILDCARD, ".+")
                    + "$";
            Pattern compiledTopicFilterRegEx = Pattern.compile(topicFilterRegEx);
            return topics.stream().filter(topic -> compiledTopicFilterRegEx.matcher(topic).matches()).collect(Collectors.toSet());
        } else {
            return topics.contains(topicFilter) ?
                    Collections.singleton(topicFilter) : Collections.emptySet();
        }
    }
}
