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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class OrderedProcessingQueueImplTest {

    OrderedProcessingQueue orderedProcessingQueue;

    @Before
    public void setUp() {
        orderedProcessingQueue = spy(new OrderedProcessingQueueImpl(10));
    }

    @Test
    public void givenEmptyQueue_whenAddMoreThanLimitAwaiting_thenThrowException() {
        for (int i = 0; i < 10; i++) {
            final int msgId = i;
            assertDoesNotThrow(() -> orderedProcessingQueue.addAwaiting(msgId));
        }
        assertThrows(FullMsgQueueException.class, () -> orderedProcessingQueue.addAwaiting(10));
    }

    @Test
    public void givenNonEmptyQueue_whenFinishMsgs_thenReturnExpectedResult() {
        assertThrows(FullMsgQueueException.class, () -> orderedProcessingQueue.finish(0));

        orderedProcessingQueue.addAwaiting(1);
        orderedProcessingQueue.addAwaiting(2);
        orderedProcessingQueue.addAwaiting(3);

        List<Integer> finished = orderedProcessingQueue.finish(5);
        assertTrue(CollectionUtils.isEmpty(finished));
        finished = orderedProcessingQueue.finish(4);
        assertTrue(CollectionUtils.isEmpty(finished));
        finished = orderedProcessingQueue.finish(3);
        assertTrue(CollectionUtils.isEmpty(finished));
        assertThrows(FullMsgQueueException.class, () -> orderedProcessingQueue.finish(2));

        finished = orderedProcessingQueue.finish(1);
        assertEquals(List.of(1, 2, 3), finished);
    }

    @Test
    public void givenNonEmptyQueue_whenFinishMsgInOrder_thenReturnResult() {
        orderedProcessingQueue.addAwaiting(1);
        List<Integer> finished = orderedProcessingQueue.finish(1);
        assertEquals(List.of(1), finished);

        orderedProcessingQueue.addAwaiting(2);
        finished = orderedProcessingQueue.finish(2);
        assertEquals(List.of(2), finished);

        orderedProcessingQueue.addAwaiting(3);
        finished = orderedProcessingQueue.finish(3);
        assertEquals(List.of(3), finished);
    }

    @Test
    public void givenNonEmptyQueue_whenFinishAllMsgs_thenReturnResult() {
        orderedProcessingQueue.addAwaiting(1);
        orderedProcessingQueue.addAwaiting(1);
        orderedProcessingQueue.addAwaiting(2);
        orderedProcessingQueue.addAwaiting(2);
        orderedProcessingQueue.addAwaiting(3);
        orderedProcessingQueue.addAwaiting(3);

        List<Integer> finished = orderedProcessingQueue.finishAll(3);
        assertTrue(CollectionUtils.isEmpty(finished));

        finished = orderedProcessingQueue.finishAll(1);
        assertEquals(List.of(1, 1), finished);

        finished = orderedProcessingQueue.finishAll(2);
        assertEquals(List.of(2, 2, 3, 3), finished);
    }
}