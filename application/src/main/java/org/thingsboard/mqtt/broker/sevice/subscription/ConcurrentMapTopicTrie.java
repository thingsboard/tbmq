/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.sevice.subscription;

import lombok.AllArgsConstructor;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ConcurrentMapTopicTrie<T> implements TopicTrie<T> {
    private final AtomicInteger size = new AtomicInteger();
    private final Node<T> root = new Node<>();

    private static class Node<T> {
        private String key;
        private final ConcurrentMap<String, Node<T>> children = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<T> values = new ConcurrentLinkedQueue<>();

        public Node() {
        }

        public Node(String key) {
            this.key = key;
        }
    }

    public ConcurrentMapTopicTrie() {
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public List<T> get(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        List<T> result = new ArrayList<>();
        Stack<TopicPosition<T>> topicPositions = new Stack<>();
        topicPositions.add(new TopicPosition<>(0, root));

        while (!topicPositions.isEmpty()) {
            TopicPosition<T> topicPosition = topicPositions.pop();
            if (topicPosition.prevDelimiterIndex >= topic.length()) {
                result.addAll(topicPosition.node.values);
                continue;
            }
            ConcurrentMap<String, Node<T>> childNodes = topicPosition.node.children;
            Node<T> multiLevelWildcardSubs = childNodes.get(BrokerConstants.MULTI_LEVEL_WILDCARD);
            if (multiLevelWildcardSubs != null) {
                result.addAll(multiLevelWildcardSubs.values);
            }
            String segment = getSegment(topic, topicPosition.prevDelimiterIndex);
            int nextDelimiterIndex = topicPosition.prevDelimiterIndex + segment.length() + 1;
            Node<T> singleLevelWildcardSubs = childNodes.get(BrokerConstants.SINGLE_LEVEL_WILDCARD);
            if (singleLevelWildcardSubs != null) {
                topicPositions.add(new TopicPosition<>(nextDelimiterIndex, singleLevelWildcardSubs));
            }
            Node<T> segmentNode = childNodes.get(segment);
            if (segmentNode != null) {
                topicPositions.add(new TopicPosition<>(nextDelimiterIndex, segmentNode));
            }
        }
        return result;
    }

    @AllArgsConstructor
    private static class TopicPosition<T> {
        private final int prevDelimiterIndex;
        private final Node<T> node;
    }

    @Override
    public void put(String topicFilter, T val) {
        if (topicFilter == null || val == null) {
            throw new IllegalArgumentException("Topic filter or value cannot be null");
        }
        put(root, topicFilter, val, 0);
        size.getAndIncrement();
    }

    private void put(Node<T> x, String key, T val, int prevDelimiterIndex) {
        if (prevDelimiterIndex >= key.length()) {
            x.values.add(val);
        } else {
            String segment = getSegment(key, prevDelimiterIndex);
            Node<T> nextNode = x.children.computeIfAbsent(segment, s -> new Node<>(segment));
            put(nextNode, key, val, prevDelimiterIndex + segment.length() + 1);
        }
    }

    @Override
    public void delete(String topicFilter, Predicate<T> deletionFilter) {
        if (topicFilter == null || deletionFilter == null) {
            throw new IllegalArgumentException("Topic filter or deletionFilter cannot be null");
        }
        Node<T> x = getNode(root, topicFilter, 0);
        if (x != null) {
            List<T> valuesToDelete = x.values.stream().filter(deletionFilter).collect(Collectors.toList());
            x.values.removeAll(valuesToDelete);
            size.getAndSet(size.get() - valuesToDelete.size());
        }
    }

    private Node<T> getNode(Node<T> x, String key, int prevDelimiterIndex) {
        if (x == null) return null;
        if (prevDelimiterIndex >= key.length()) {
            return x;
        }
        String segment = getSegment(key, prevDelimiterIndex);
        return getNode(x.children.get(segment), key, prevDelimiterIndex + segment.length() + 1);
    }

    private String getSegment(String key, int prevDelimiterIndex) {
        int nextDelimitedIndex = key.indexOf(BrokerConstants.TOPIC_DELIMITER, prevDelimiterIndex);

        return nextDelimitedIndex == -1 ?
                key.substring(prevDelimiterIndex)
                : key.substring(prevDelimiterIndex, nextDelimitedIndex);
    }
}