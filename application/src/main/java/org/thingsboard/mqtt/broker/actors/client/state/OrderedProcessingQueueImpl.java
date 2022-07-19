/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/*
    Not thread-safe
 */
@RequiredArgsConstructor
public class OrderedProcessingQueueImpl implements OrderedProcessingQueue {
    private final Queue<Integer> awaitingMsgIds = new LinkedList<>();
    private final Queue<Integer> finishedMsgIds = new LinkedList<>();

    private final int maxAwaitingQueueSize;

    @Override
    public void addAwaiting(int msgId) throws FullMsgQueueException {
        if (awaitingMsgIds.size() >= maxAwaitingQueueSize) {
            throw new FullMsgQueueException("Awaiting queue size is already " + awaitingMsgIds.size());
        }
        awaitingMsgIds.add(msgId);
    }

    @Override
    public List<Integer> finish(int msgId) throws FullMsgQueueException {
        if (awaitingMsgIds.isEmpty()) {
            throw new FullMsgQueueException("Awaiting msgs queue is empty");
        }
        if (awaitingMsgIds.peek() != msgId) {
            finishedMsgIds.add(msgId);
            if (awaitingMsgIds.size() < finishedMsgIds.size()) {
                throw new FullMsgQueueException("Awaiting msgs - " + awaitingMsgIds.size() + ", finished msgs - " + finishedMsgIds.size());
            }
            return Collections.emptyList();
        }
        awaitingMsgIds.poll();
        LinkedList<Integer> orderedFinishedMsgs = getOrderedFinishedMsgs();
        orderedFinishedMsgs.addFirst(msgId);
        return orderedFinishedMsgs;
    }

    @Override
    public List<Integer> finishAll(int msgId) throws FullMsgQueueException {
        long numberOfAwaitingMsgs = awaitingMsgIds.stream().filter(i -> i == msgId).count();
        LinkedList<Integer> orderedFinishedMsgs = new LinkedList<>();
        for (int i = 0; i < numberOfAwaitingMsgs; i++) {
            orderedFinishedMsgs.addAll(finish(msgId));
        }
        return orderedFinishedMsgs;
    }

    private LinkedList<Integer> getOrderedFinishedMsgs() {
        LinkedList<Integer> orderedFinishedMsgs = new LinkedList<>();
        boolean isSequenceBroken = false;
        while (!awaitingMsgIds.isEmpty() && !isSequenceBroken) {
            Integer firstAwaitingMsg = awaitingMsgIds.peek();
            if (finishedMsgIds.contains(firstAwaitingMsg)) {
                finishedMsgIds.remove(firstAwaitingMsg);
                awaitingMsgIds.poll();
                orderedFinishedMsgs.add(firstAwaitingMsg);
            } else {
                isSequenceBroken = true;
            }
        }

        return orderedFinishedMsgs;
    }
}
