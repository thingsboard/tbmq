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
package org.thingsboard.mqtt.broker.session;

import io.netty.handler.codec.mqtt.MqttProperties;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Getter
public class TopicAliasCtx {

    public static final TopicAliasCtx DISABLED_TOPIC_ALIASES = new TopicAliasCtx(false, 0, null, null);
    public static final String UNKNOWN_TOPIC_ALIAS_MSG = "Unknown Topic Alias!";

    private final boolean enabled;
    private final int maxTopicAlias;
    private final ConcurrentMap<Integer, String> clientMappings;
    private final ConcurrentMap<String, Integer> serverMappings;

    public TopicAliasCtx(boolean enabled, int maxTopicAlias) {
        this(enabled, maxTopicAlias, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public TopicAliasCtx(boolean enabled, int maxTopicAlias,
                         ConcurrentMap<Integer, String> clientMappings,
                         ConcurrentMap<String, Integer> serverMappings) {
        this.enabled = enabled;
        this.maxTopicAlias = maxTopicAlias;
        this.clientMappings = clientMappings;
        this.serverMappings = serverMappings;
    }

    public String getTopicNameByAlias(PublishMsg publishMsg) {
        if (enabled) {
            var receivedTopicName = publishMsg.getTopicName();
            var topicAliasProperty = MqttPropertiesUtil.getTopicAliasProperty(publishMsg.getProperties());
            if (topicAliasProperty != null) {
                int topicAlias = topicAliasProperty.value();
                validateTopicAlias(topicAlias);

                if (receivedTopicName.isEmpty()) {
                    var topicName = getTopicByAlias(topicAlias);
                    if (topicName == null) {
                        throw new MqttException(UNKNOWN_TOPIC_ALIAS_MSG);
                    } else {
                        return topicName;
                    }
                } else {
                    saveMapping(topicAlias, receivedTopicName);
                    return receivedTopicName;
                }
            }
        }
        return null;
    }

    public PublishMsg createPublishMsgUsingTopicAlias(PublishMsg publishMsg, int minTopicNameLengthForAliasReplacement) {
        if (enabled) {
            boolean setEmptyTopic = updateTopicAlias(publishMsg.getTopicName(), publishMsg.getProperties(), minTopicNameLengthForAliasReplacement);
            if (setEmptyTopic) {
                return publishMsg.toBuilder().topicName(BrokerConstants.EMPTY_STR).build();
            }
        }
        return publishMsg;
    }

    public DevicePublishMsg createPublishMsgUsingTopicAlias(DevicePublishMsg publishMsg, int minTopicNameLengthForAliasReplacement) {
        if (enabled) {
            boolean setEmptyTopic = updateTopicAlias(publishMsg.getTopic(), publishMsg.getProperties(), minTopicNameLengthForAliasReplacement);
            if (setEmptyTopic) {
                return publishMsg.toBuilder().topic(BrokerConstants.EMPTY_STR).build();
            }
        }
        return publishMsg;
    }

    private boolean updateTopicAlias(String topicName, MqttProperties properties, int minTopicNameLengthForAliasReplacement) {
        if (topicName.length() <= minTopicNameLengthForAliasReplacement) {
            return false;
        }
        Integer topicAlias = serverMappings.get(topicName);
        if (topicAlias != null) {
            MqttPropertiesUtil.addTopicAliasToProps(properties, topicAlias);
            return true;
        }
        int nextTopicAlias = getNextTopicAlias(topicName);
        if (nextTopicAlias != 0) {
            MqttPropertiesUtil.addTopicAliasToProps(properties, nextTopicAlias);
        }
        return false;
    }

    public TopicAliasResult getTopicAliasResult(PublishMsgProto publishMsgProto, int minTopicNameLengthForAliasReplacement) {
        if (enabled) {
            String topicName = publishMsgProto.getTopicName();
            if (topicName.length() > minTopicNameLengthForAliasReplacement) {
                Integer topicAlias = serverMappings.get(topicName);
                if (topicAlias == null) {
                    int nextTopicAlias = getNextTopicAlias(topicName);
                    if (nextTopicAlias == 0) {
                        return null;
                    }
                    return new TopicAliasResult(topicName, nextTopicAlias);
                }
                return new TopicAliasResult(BrokerConstants.EMPTY_STR, topicAlias);
            }
        }
        return null;
    }

    void validateTopicAlias(int topicAlias) {
        if (topicAlias == 0) {
            throw new MqttException("Topic Alias is zero.");
        }
        if (topicAlias > maxTopicAlias) {
            throw new MqttException("Topic Alias " + topicAlias + " can not be greater than Max Topic Alias " + maxTopicAlias);
        }
    }

    @Nullable
    private String getTopicByAlias(int topicAlias) {
        return clientMappings.get(topicAlias);
    }

    private void saveMapping(int topicAlias, String topicName) {
        clientMappings.put(topicAlias, topicName);
    }

    int getNextTopicAlias(String topicName) {
        if (isMoreTopicAliasAvailable()) {
            int lastTopicAlias = serverMappings.size();
            int nextTopicAlias = lastTopicAlias + 1;
            serverMappings.put(topicName, nextTopicAlias);
            return nextTopicAlias;
        }
        return 0;
    }

    private boolean isMoreTopicAliasAvailable() {
        return currentTopicAliasesCount() < maxTopicAlias;
    }

    private int currentTopicAliasesCount() {
        return clientMappings.size() + serverMappings.size();
    }
}
