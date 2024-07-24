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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.RetainMsgTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
public class ConcurrentMapRetainMsgTrie<T> implements RetainMsgTrie<T> {

    private final AtomicInteger size;
    private final AtomicLong nodesCount;
    private final Node<T> root = new Node<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Setter
    @Value("${mqtt.retain-msg-trie.wait-for-clear-lock-ms}")
    private int waitForClearLockMs;

    public ConcurrentMapRetainMsgTrie(StatsManager statsManager) {
        this.size = statsManager.createRetainMsgSizeCounter();
        this.nodesCount = statsManager.createRetainMsgTrieNodesCounter();
    }

    @EqualsAndHashCode
    private static class Node<T> {
        private final AtomicReference<T> value = new AtomicReference<>();
        private final ConcurrentMap<String, Node<T>> children = new ConcurrentHashMap<>();
        private String key;

        public Node() {
        }

        public Node(String key) {
            this.key = key;
        }
    }

    @Override
    public List<T> get(String topicFilter) {
        if (topicFilter == null) {
            throw new IllegalArgumentException("Topic filter cannot be null");
        }
        List<T> result = new ArrayList<>();
        Stack<TopicPosition<T>> topicPositions = new Stack<>();
        topicPositions.add(new TopicPosition<>(0, root, false));

        while (!topicPositions.isEmpty()) {
            TopicPosition<T> topicPosition = topicPositions.pop();
            ConcurrentMap<String, Node<T>> childNodes = topicPosition.node.children;
            T value = topicPosition.node.value.get();
            if (topicPosition.isMultiLevelWildcard) {
                if (value != null) {
                    result.add(value);
                }
                for (Node<T> childNode : childNodes.values()) {
                    topicPositions.add(new TopicPosition<>(0, childNode, true));
                }
                continue;
            }
            if (topicPosition.segmentStartIndex > topicFilter.length()) {
                if (value != null) {
                    result.add(value);
                }
                continue;
            }
            String segment = getSegment(topicFilter, topicPosition.segmentStartIndex);
            int nextSegmentStartIndex = getNextSegmentStartIndex(topicPosition.segmentStartIndex, segment);
            if (segment.equals(BrokerConstants.MULTI_LEVEL_WILDCARD)) {
                childNodes.values().stream()
                        .filter(childNode -> notStartingWith$(topicPosition, childNode))
                        .forEach(childNode -> topicPositions.add(new TopicPosition<>(0, childNode, true)));
                if (value != null) {
                    result.add(value);
                }
            } else if (segment.equals(BrokerConstants.SINGLE_LEVEL_WILDCARD)) {
                childNodes.values().stream()
                        .filter(childNode -> notStartingWith$(topicPosition, childNode))
                        .forEach(childNode -> topicPositions.add(new TopicPosition<>(nextSegmentStartIndex, childNode, false)));
            } else {
                Node<T> segmentNode = childNodes.get(segment);
                if (segmentNode != null) {
                    topicPositions.add(new TopicPosition<>(nextSegmentStartIndex, segmentNode, false));
                }
            }
        }
        return result;
    }

    private boolean notStartingWith$(TopicPosition<T> topicPosition, Node<T> childNode) {
        return topicPosition.segmentStartIndex != 0 || childNode.key.isEmpty() || childNode.key.charAt(0) != '$';
    }

    @AllArgsConstructor
    private static class TopicPosition<T> {
        private final int segmentStartIndex;
        private final Node<T> node;
        private final boolean isMultiLevelWildcard;
    }

    @Override
    public void put(String topic, T val) {
        log.trace("Executing put [{}] [{}]", topic, val);
        if (topic == null || val == null) {
            throw new IllegalArgumentException("Topic or value cannot be null");
        }
        lock.readLock().lock();
        try {
            put(root, topic, val, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void put(Node<T> x, String topic, T val, int segmentStartIndex) {
        if (segmentStartIndex > topic.length()) {
            T prevValue = x.value.getAndSet(val);
            if (prevValue == null) {
                size.getAndIncrement();
            }
        } else {
            String segment = getSegment(topic, segmentStartIndex);
            Node<T> nextNode = x.children.computeIfAbsent(segment, s -> {
                nodesCount.incrementAndGet();
                return new Node<>(segment);
            });
            put(nextNode, topic, val, getNextSegmentStartIndex(segmentStartIndex, segment));
        }
    }

    @Override
    public void delete(String topic) {
        log.trace("Executing delete [{}]", topic);
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        Node<T> x = getDeleteNode(root, topic, 0);
        if (x != null) {
            T prevValue = x.value.getAndSet(null);
            if (prevValue != null) {
                size.decrementAndGet();
            }
        }
    }

    private Node<T> getDeleteNode(Node<T> x, String topic, int segmentStartIndex) {
        if (x == null) {
            return null;
        }
        if (segmentStartIndex > topic.length()) {
            return x;
        }
        String segment = getSegment(topic, segmentStartIndex);
        return getDeleteNode(x.children.get(segment), topic, getNextSegmentStartIndex(segmentStartIndex, segment));
    }

    private int getNextSegmentStartIndex(int segmentStartIndex, String segment) {
        return segmentStartIndex + segment.length() + 1;
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void clearEmptyNodes() throws RetainMsgTrieClearException {
        log.trace("Executing clearEmptyNodes");
        acquireClearTrieLock();
        long nodesBefore = nodesCount.get();
        long clearStartTime = System.currentTimeMillis();
        try {
            clearEmptyChildren(root);
            long nodesAfter = nodesCount.get();
            long clearEndTime = System.currentTimeMillis();
            if (log.isDebugEnabled()) {
                log.debug("Clearing trie took {} ms, cleared {} nodes.",
                        clearEndTime - clearStartTime, nodesBefore - nodesAfter);
            }
        } catch (Exception e) {
            long nodesAfter = nodesCount.get();
            log.error("Failed on clearing empty nodes. Managed to clear {} nodes.",
                    nodesBefore - nodesAfter, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean clearEmptyChildren(ConcurrentMapRetainMsgTrie.Node<T> node) {
        boolean isNodeEmpty = node.value.get() == null;
        for (Map.Entry<String, ConcurrentMapRetainMsgTrie.Node<T>> entry : node.children.entrySet()) {
            ConcurrentMapRetainMsgTrie.Node<T> value = entry.getValue();
            boolean isChildEmpty = clearEmptyChildren(value);
            if (isChildEmpty) {
                node.children.remove(entry.getKey());
                nodesCount.decrementAndGet();
            } else {
                isNodeEmpty = false;
            }
        }

        return isNodeEmpty;
    }

    private void acquireClearTrieLock() throws RetainMsgTrieClearException {
        boolean successfullyAcquiredLock = false;
        try {
            successfullyAcquiredLock = lock.writeLock().tryLock(waitForClearLockMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("Acquiring lock was interrupted.");
        }
        if (!successfullyAcquiredLock) {
            throw new RetainMsgTrieClearException("Couldn't acquire lock for clearing trie. " +
                    "There are a lot of clients sending retained messages right now.");
        }
    }

    private String getSegment(String key, int segmentStartIndex) {
        int nextDelimiterIndex = key.indexOf(BrokerConstants.TOPIC_DELIMITER, segmentStartIndex);

        return nextDelimiterIndex == -1 ?
                key.substring(segmentStartIndex)
                : key.substring(segmentStartIndex, nextDelimiterIndex);
    }
}
