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
package org.thingsboard.mqtt.broker.sevice.retain;

import lombok.AllArgsConstructor;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentMapRetainMsgTrie<T> implements RetainMsgTrie<T> {
    private final AtomicInteger size = new AtomicInteger();
    private final Node<T> root = new Node<>();

    private static class Node<T> {
        private final AtomicReference<T> value = new AtomicReference<>();
        private String key;
        private final ConcurrentMap<String, Node<T>> children = new ConcurrentHashMap<>();

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
            if (topicPosition.prevDelimiterIndex >= topicFilter.length()) {
                if (value != null) {
                    result.add(value);
                }
                continue;
            }
            String segment = getSegment(topicFilter, topicPosition.prevDelimiterIndex);
            int nextDelimiterIndex = topicPosition.prevDelimiterIndex + segment.length() + 1;
            if (segment.equals(BrokerConstants.MULTI_LEVEL_WILDCARD)) {
                for (Node<T> childNode : childNodes.values()) {
                    topicPositions.add(new TopicPosition<>(0, childNode, true));
                }
            } else if (segment.equals(BrokerConstants.SINGLE_LEVEL_WILDCARD)) {
                for (Node<T> childNode : childNodes.values()) {
                    topicPositions.add(new TopicPosition<>(nextDelimiterIndex, childNode, false));
                }
            } else {
                Node<T> segmentNode = childNodes.get(segment);
                if (segmentNode != null) {
                    topicPositions.add(new TopicPosition<>(nextDelimiterIndex, segmentNode, false));
                }
            }
        }
        return result;
    }

    @AllArgsConstructor
    private static class TopicPosition<T> {
        private final int prevDelimiterIndex;
        private final Node<T> node;
        private final boolean isMultiLevelWildcard;
    }

    @Override
    public void put(String topic, T val) {
        if (topic == null || val == null) {
            throw new IllegalArgumentException("Topic or value cannot be null");
        }
        put(root, topic, val, 0);
    }

    private void put(Node<T> x, String topic, T val, int prevDelimiterIndex) {
        if (prevDelimiterIndex >= topic.length()) {
            T prevValue = x.value.getAndSet(val);
            if (prevValue == null){
                size.getAndIncrement();
            }
        } else {
            String segment = getSegment(topic, prevDelimiterIndex);
            Node<T> nextNode = x.children.computeIfAbsent(segment, s -> new Node<>(segment));
            put(nextNode, topic, val, prevDelimiterIndex + segment.length() + 1);
        }
    }

    @Override
    public void delete(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        Node<T> x = getNode(root, topic, 0);
        if (x != null) {
            T prevValue = x.value.getAndSet(null);
            if (prevValue != null) {
                size.decrementAndGet();
            }
        }
    }

    private Node<T> getNode(Node<T> x, String topic, int prevDelimiterIndex) {
        if (x == null) return null;
        if (prevDelimiterIndex >= topic.length()) {
            return x;
        }
        String segment = getSegment(topic, prevDelimiterIndex);
        return getNode(x.children.get(segment), topic, prevDelimiterIndex + segment.length() + 1);
    }

    @Override
    public int size() {
        return size.get();
    }

    private String getSegment(String key, int prevDelimiterIndex) {
        int nextDelimitedIndex = key.indexOf(BrokerConstants.TOPIC_DELIMITED, prevDelimiterIndex);

        return nextDelimitedIndex == -1 ?
                key.substring(prevDelimiterIndex)
                : key.substring(prevDelimiterIndex, nextDelimitedIndex);
    }
}
