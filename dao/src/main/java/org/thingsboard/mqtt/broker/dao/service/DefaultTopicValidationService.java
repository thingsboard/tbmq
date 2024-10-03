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
package org.thingsboard.mqtt.broker.dao.service;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MULTI_LEVEL_WILDCARD;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SINGLE_LEVEL_WILDCARD;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOPIC_DELIMITER;

@Setter
@Service
public class DefaultTopicValidationService implements TopicValidationService {

    static final int MAX_SIZE_BYTES = 65535;

    @Value("${mqtt.topic.max-segments-count}")
    private int maxSegmentsCount;

    @Override
    public void validateTopic(String topic) {
        validateTopicNameAndFilter(topic);

        if (topic.contains(MULTI_LEVEL_WILDCARD)
                || topic.contains(SINGLE_LEVEL_WILDCARD)) {
            throw new DataValidationException("Topic name cannot contain wildcard characters!");
        }
        if (topic.startsWith("$")) {
            throw new DataValidationException("Topic name cannot start with $ character!");
        }
    }

    @Override
    public void validateTopicFilter(String topicFilter) {
        validateTopicNameAndFilter(topicFilter);

        validateMultiLevelWildcard(topicFilter);
        validateSingleLevelWildcard(topicFilter);
    }

    private void validateSingleLevelWildcard(String topicFilter) {
        for (int wildcardIndex = topicFilter.indexOf(SINGLE_LEVEL_WILDCARD);
             wildcardIndex != -1;
             wildcardIndex = topicFilter.indexOf(SINGLE_LEVEL_WILDCARD, wildcardIndex + 1)) {
            if (wildcardIndex != 0 && topicFilter.charAt(wildcardIndex - 1) != TOPIC_DELIMITER) {
                throw new DataValidationException("Single-level wildcard cannot have any character before it except '/'.");
            }
            if (wildcardIndex != topicFilter.length() - 1 && topicFilter.charAt(wildcardIndex + 1) != TOPIC_DELIMITER) {
                throw new DataValidationException("Single-level wildcard cannot have any character after it except '/'.");
            }
        }
    }

    private void validateMultiLevelWildcard(String topicFilter) {
        int wildcardIndex = topicFilter.indexOf(MULTI_LEVEL_WILDCARD);
        if (wildcardIndex == -1) {
            return;
        }
        if (wildcardIndex != topicFilter.length() - 1) {
            throw new DataValidationException("Multi-level wildcard must be the last character in the topic filter.");
        }
        if (wildcardIndex != 0 && topicFilter.charAt(wildcardIndex - 1) != TOPIC_DELIMITER) {
            throw new DataValidationException("Multi-level wildcard must be at the beginning or after / char.");
        }
    }

    private void validateTopicNameAndFilter(String topic) {
        if (topic.isEmpty()) {
            throw new DataValidationException("Topic Names and Topic Filters must be at least one character long.");
        }
        if (topic.contains(BrokerConstants.NULL_CHAR_STR)) {
            throw new DataValidationException("Topic Names and Topic Filters must not include the null character (Unicod U+0000).");
        }
        if (topic.length() > MAX_SIZE_BYTES) {
            throw new DataValidationException("Topic Names and Topic Filters must not encode to more than " + MAX_SIZE_BYTES + " bytes.");
        }
        if (maxSegmentsCount > 0) {
            int segmentsCount = StringUtils.countMatches(topic, TOPIC_DELIMITER);
            if (segmentsCount > maxSegmentsCount) {
                throw new DataValidationException("Topic Names and Topic Filters must contain less than " + maxSegmentsCount + " segments.");
            }
        }
    }
}
