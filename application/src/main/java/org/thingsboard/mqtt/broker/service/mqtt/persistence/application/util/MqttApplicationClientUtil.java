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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MqttApplicationClientUtil {

    public static final String ALPHANUMERIC_REGEX = "^[a-zA-Z0-9]+$";
    public static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile(ALPHANUMERIC_REGEX);
    public static final String SHARED_APP_TOPIC_REGEX = "^[a-zA-Z0-9/+#]+$";
    public static final Pattern SHARED_APP_TOPIC_PATTERN = Pattern.compile(SHARED_APP_TOPIC_REGEX);
    public static final Pattern SLASH_PATTERN = Pattern.compile("/");
    public static final Pattern SINGLE_LVL_WILDCARD_PATTERN = Pattern.compile("\\+");
    public static final Pattern MULTI_LVL_WILDCARD_PATTERN = Pattern.compile("#");
    private static final HashFunction HASH_FUNCTION = Hashing.sha256();

    private static final String SINGLE_LVL_WILDCARD_ABBREV = "slw";
    private static final String MULTI_LVL_WILDCARD_ABBREV = "mlw";
    private static final String APP_CLIENT_TOPIC_PREFIX = "tbmq.msg.app.";
    private static final String APP_SHARED_TOPIC_PREFIX = APP_CLIENT_TOPIC_PREFIX + "shared.";
    private static final String CONSUMER_GROUP_PREFIX = "application-persisted-msg-consumer-group-";
    private static final String SHARED_CONSUMER_GROUP_PREFIX = "application-shared-msg-consumer-group-";

    public static String getAppTopic(String clientId, boolean validateClientId) {
        if (validateClientId) {
            Matcher matcher = ALPHANUMERIC_PATTERN.matcher(clientId);
            if (matcher.matches()) {
                return constructAppTopic(clientId);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Using hash func for client id conversion: {}", clientId);
                }
                String clientIdHash = HASH_FUNCTION.hashString(clientId, StandardCharsets.UTF_8).toString();
                return constructAppTopic(clientIdHash);
            }
        }
        return constructAppTopic(clientId);
    }

    private static String constructAppTopic(String clientId) {
        return APP_CLIENT_TOPIC_PREFIX + clientId;
    }

    public static String getAppConsumerGroup(String clientId) {
        return CONSUMER_GROUP_PREFIX + clientId;
    }

    // Note, replacing slashes, single and multi levels mqtt wildcards since Kafka does not support such chars for topic name.
    // Or using hash func if other special chars are present
    public static String getSharedAppTopic(String topicFilter, boolean validateTopicFilter) {
        if (validateTopicFilter) {
            Matcher matcher = SHARED_APP_TOPIC_PATTERN.matcher(topicFilter);
            if (matcher.matches()) {
                return getReplacedSharedAppTopicFilter(topicFilter);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Using hash func for topic filter conversion: {}", topicFilter);
                }
                String topicFilterHash = HASH_FUNCTION.hashString(topicFilter, StandardCharsets.UTF_8).toString();
                return constructAppSharedTopic(topicFilterHash);
            }
        }
        return getReplacedSharedAppTopicFilter(topicFilter);
    }

    static String getReplacedSharedAppTopicFilter(String topicFilter) {
        String s1 = SLASH_PATTERN.matcher(topicFilter).replaceAll(BrokerConstants.DOT);
        String s2 = SINGLE_LVL_WILDCARD_PATTERN.matcher(s1).replaceAll(SINGLE_LVL_WILDCARD_ABBREV);
        String s3 = MULTI_LVL_WILDCARD_PATTERN.matcher(s2).replaceFirst(MULTI_LVL_WILDCARD_ABBREV);
        return constructAppSharedTopic(s3);
    }

    private static String constructAppSharedTopic(String topicFilter) {
        return APP_SHARED_TOPIC_PREFIX + topicFilter;
    }

    public static String getSharedAppConsumerGroup(TopicSharedSubscription subscription, String sharedAppTopic) {
        return SHARED_CONSUMER_GROUP_PREFIX + subscription.getShareName() + "-" + sharedAppTopic;
    }
}
