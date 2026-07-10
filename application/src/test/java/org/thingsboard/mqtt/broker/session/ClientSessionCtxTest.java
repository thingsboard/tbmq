/**
 * Copyright © 2016-2026 The Thingsboard Authors
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

import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ClientSessionCtxTest {

    @Test
    public void passthroughs_areNoOpWhenPublishedInFlightCtxIsNull() {
        // With flowControlEnabled=false, initPublishedInFlightCtx is never called,
        // so publishedInFlightCtx stays null. The three passthroughs must be silent no-ops.
        ClientSessionCtx sessionCtx = new ClientSessionCtx();
        sessionCtx.ackInFlightMsg(123);
        sessionCtx.onChannelWritable();
        sessionCtx.releasePublishedInFlightCtx();
        // No NPE, no side effect.
    }

    @Test
    public void closeInitiated_defaultsFalse() {
        // A freshly created session has not had a broker-side close initiated yet, so exceptionCaught
        // must be able to attribute any connection reset to the remote peer rather than to TBMQ.
        ClientSessionCtx sessionCtx = new ClientSessionCtx();
        assertFalse(sessionCtx.isCloseInitiated());
    }

    @Test
    public void closeChannel_marksCloseInitiated() {
        // closeChannel() is the single chokepoint for every broker-initiated teardown, so it must record that
        // TBMQ owns the close before the channel is flushed and closed.
        ClientSessionCtx sessionCtx = new ClientSessionCtx();
        ChannelHandlerContext channel = mock(ChannelHandlerContext.class);
        sessionCtx.setChannel(channel);

        sessionCtx.closeChannel();

        assertTrue(sessionCtx.isCloseInitiated());
        verify(channel).flush();
        verify(channel).close();
    }
}
