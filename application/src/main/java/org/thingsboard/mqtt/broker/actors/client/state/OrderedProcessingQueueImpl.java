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
package org.thingsboard.mqtt.broker.actors.client.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Not thread-safe
 */
@RequiredArgsConstructor
@Getter
public class OrderedProcessingQueueImpl implements OrderedProcessingQueue {

    private final Queue<Integer> awaitingMsgIds = new LinkedList<>();
    private final Queue<Integer> finishedMsgIds = new LinkedList<>();

    private final int maxAwaitingQueueSize;

    @Override
    public void addAwaiting(int msgId) throws FullMsgQueueException {
        if (awaitingMsgIds.size() >= maxAwaitingQueueSize) {
            throw new FullMsgQueueException("In-flight queue size is already " + awaitingMsgIds.size());
        }
        awaitingMsgIds.add(msgId);
    }

    @Override
    public List<Integer> finish(int msgId) throws FullMsgQueueException {
        if (awaitingMsgIds.isEmpty()) {
            throw new FullMsgQueueException("In-flight messages queue is empty, nothing to acknowledge");
        }
        if (awaitingMsgIds.peek() != msgId) {
            finishedMsgIds.add(msgId);
            if (awaitingMsgIds.size() < finishedMsgIds.size()) {
                throw new FullMsgQueueException("In-flight messages - " + awaitingMsgIds.size() + ", finished messages - " + finishedMsgIds.size());
            }
            return Collections.emptyList();
        }
        awaitingMsgIds.poll();
        LinkedList<Integer> orderedFinishedMessages = getOrderedFinishedMessages();
        orderedFinishedMessages.addFirst(msgId);
        return orderedFinishedMessages;
    }

    @Override
    public List<Integer> finishAll(int msgId) throws FullMsgQueueException {
        long numberOfAwaitingMessages = getCountOfAwaitingMessagesById(msgId);
        LinkedList<Integer> orderedFinishedMessages = new LinkedList<>();
        for (int i = 0; i < numberOfAwaitingMessages; i++) {
            orderedFinishedMessages.addAll(finish(msgId));
        }
        return orderedFinishedMessages;
    }

    private LinkedList<Integer> getOrderedFinishedMessages() {
        LinkedList<Integer> orderedFinishedMessages = new LinkedList<>();
        boolean isSequenceBroken = false;
        while (!awaitingMsgIds.isEmpty() && !isSequenceBroken) {
            int firstAwaitingMsg = awaitingMsgIds.peek();
            if (finishedMsgIds.contains(firstAwaitingMsg)) {
                finishedMsgIds.remove(firstAwaitingMsg);
                awaitingMsgIds.poll();
                orderedFinishedMessages.add(firstAwaitingMsg);
            } else {
                isSequenceBroken = true;
            }
        }
        return orderedFinishedMessages;
    }

    private long getCountOfAwaitingMessagesById(int msgId) {
        int count = 0;
        for (int i : awaitingMsgIds) {
            if (i == msgId) {
                count++;
            }
        }
        return count;
    }

}
