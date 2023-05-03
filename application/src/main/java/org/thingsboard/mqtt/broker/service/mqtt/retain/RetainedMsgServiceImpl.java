/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.RetainMsgTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.RetainedMsgTimerStats;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RetainedMsgServiceImpl implements RetainedMsgService {

    private final RetainMsgTrie<RetainedMsg> retainMsgTrie;
    private final RetainedMsgTimerStats retainedMsgTimerStats;

    public RetainedMsgServiceImpl(RetainMsgTrie<RetainedMsg> retainMsgTrie, StatsManager statsManager) {
        this.retainMsgTrie = retainMsgTrie;
        this.retainedMsgTimerStats = statsManager.getRetainedMsgTimerStats();
    }

    @Override
    public void saveRetainedMsg(String topic, RetainedMsg retainedMsg) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveRetainedMsg [{}] [{}]", topic, retainedMsg);
        }
        retainMsgTrie.put(topic, retainedMsg);
    }

    @Override
    public void clearRetainedMsg(String topic) {
        if (log.isTraceEnabled()) {
            log.trace("Executing clearRetainedMsg [{}]", topic);
        }
        retainMsgTrie.delete(topic);
    }

    @Override
    public List<RetainedMsg> getRetainedMessages(String topicFilter) {
        long startTime = System.nanoTime();
        List<RetainedMsg> retainedMsg = retainMsgTrie.get(topicFilter);
        retainedMsgTimerStats.logRetainedMsgLookup(startTime, TimeUnit.NANOSECONDS);
        return retainedMsg;
    }

    @Override
    public void clearEmptyTopicNodes() throws RetainMsgTrieClearException {
        if (log.isTraceEnabled()) {
            log.trace("Executing clearEmptyTopicNodes");
        }
        retainMsgTrie.clearEmptyNodes();
    }

    @Scheduled(cron = "${mqtt.retain-msg-trie.clear-nodes-cron}", zone = "${mqtt.retain-msg-trie.clear-nodes-zone}")
    void scheduleEmptyNodeClear() {
        log.info("Start clearing empty nodes in RetainMsgTrie");
        try {
            retainMsgTrie.clearEmptyNodes();
        } catch (RetainMsgTrieClearException e) {
            log.error("Failed to clear empty nodes.", e);
        }
    }
}
