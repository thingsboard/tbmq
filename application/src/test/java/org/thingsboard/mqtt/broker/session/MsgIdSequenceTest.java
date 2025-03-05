/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MsgIdSequenceTest {

    private MsgIdSequence msgIdSequence;

    @Before
    public void setUp() {
        msgIdSequence = new MsgIdSequence();
    }

    @Test
    public void testNextMsgId() {
        int firstMsgId = msgIdSequence.nextMsgId();
        assertEquals(1, firstMsgId);

        int secondMsgId = msgIdSequence.nextMsgId();
        assertEquals(2, secondMsgId);
    }

    @Test
    public void testMsgIdWrapAround() {
        msgIdSequence.updateMsgIdSequence(0xfffe);
        int msgId = msgIdSequence.nextMsgId();
        assertEquals(0xffff, msgId);

        msgId = msgIdSequence.nextMsgId();
        assertEquals(1, msgId);
    }

    @Test
    public void testUpdateMsgIdSequence() {
        msgIdSequence.updateMsgIdSequence(100);
        assertEquals(101, msgIdSequence.nextMsgId());

        msgIdSequence.updateMsgIdSequence(0xfffe);
        assertEquals(0xffff, msgIdSequence.nextMsgId());

        msgIdSequence.updateMsgIdSequence(0xffff);
        assertEquals(1, msgIdSequence.nextMsgId());
    }

    @Test
    public void testGetCurrentSeq() {
        assertEquals(1, msgIdSequence.getCurrentSeq());

        msgIdSequence.nextMsgId();
        assertEquals(2, msgIdSequence.getCurrentSeq());

        msgIdSequence.updateMsgIdSequence(50);
        assertEquals(51, msgIdSequence.getCurrentSeq());
    }

    @Test
    public void testFullSequence() {
        for (int i = 0; i < 0xffff; i++) {
            int msgId = msgIdSequence.nextMsgId();
            assertEquals(i + 1, msgId);
            if (i == 0xffff - 1) {
                assertEquals(0xffff, msgId);
            }
        }
        int msgId = msgIdSequence.nextMsgId();
        assertEquals(1, msgId);
    }
}
