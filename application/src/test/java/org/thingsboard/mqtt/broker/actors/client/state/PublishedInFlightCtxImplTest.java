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
package org.thingsboard.mqtt.broker.actors.client.state;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.mqtt.MqttPubMsgWithCreatedTime;
import org.thingsboard.mqtt.broker.service.mqtt.DefaultMqttMessageCreator;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PublishedInFlightCtxImplTest {

    private final DefaultMqttMessageCreator mqttMessageCreator = new DefaultMqttMessageCreator();

    PublishedInFlightCtxImpl publishedInFlightCtx;
    FlowControlService flowControlService;
    ClientSessionCtx clientSessionCtx;

    @Before
    public void setUp() throws Exception {
        flowControlService = mock(FlowControlService.class);
        clientSessionCtx = mock(ClientSessionCtx.class);

        when(clientSessionCtx.getClientId()).thenReturn("test");

        publishedInFlightCtx = spy(new PublishedInFlightCtxImpl(flowControlService, clientSessionCtx, 10, 10));
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void givenQoS0Msg_whenAddInFlightMsg_thenInFlightQueueIsNotIncremented() {
        boolean result = publishedInFlightCtx.addInFlightMsg(newAtMostOnceMqttPubMsg());

        assertEquals(0, publishedInFlightCtx.getPublishedInFlightMsgCounter().get());
        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());

        assertTrue(result);
    }

    @Test
    public void givenQoS1Msg_whenAddInFlightMsg_thenInFlightQueueIsIncremented() {
        boolean result = publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));

        assertEquals(1, publishedInFlightCtx.getPublishedInFlightMsgCounter().get());
        assertFalse(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());

        assertTrue(result);
    }

    @Test
    public void givenPublishedInFlightLimitReached_whenAddInFlightMsg_thenDelayQueueIsIncremented() {
        publishedInFlightCtx.getPublishedInFlightMsgCounter().set(10);

        boolean result = publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));

        assertEquals(1, publishedInFlightCtx.getDelayedMsgCounter().get());
        verify(flowControlService, times(1)).addToMap(eq("test"), eq(publishedInFlightCtx));

        assertFalse(result);
    }

    @Test
    public void givenDelayQueueLimitReached_whenAddInFlightMsg_thenMsgIsSkipped() {
        publishedInFlightCtx.getDelayedMsgCounter().set(10);

        boolean result = publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));

        assertEquals(10, publishedInFlightCtx.getDelayedMsgCounter().get());

        assertFalse(result);
    }

    @Test
    public void givenPublishedInFlightLimitReachedAndDelayQueueLimitIsNotReached_whenAddInFlightMsg_thenDelayQueueIsIncremented() {
        publishedInFlightCtx.getDelayedMsgCounter().set(4);

        boolean result = publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));

        assertEquals(5, publishedInFlightCtx.getDelayedMsgCounter().get());

        assertFalse(result);
    }

    @Test
    public void givenEmptyInFlightQueue_whenAckInFlightMsg_thenDoNothing() {
        publishedInFlightCtx.ackInFlightMsg(1);

        assertEquals(0, publishedInFlightCtx.getPublishedInFlightMsgCounter().get());
    }

    @Test
    public void givenInFlightQueueNotEmpty_whenWrongAckInFlightMsgReceived_thenReceivedAckMsgInWrongOrderQueueIsIncremented() {
        publishedInFlightCtx.getPublishedInFlightMsgQueue().add(1);

        publishedInFlightCtx.ackInFlightMsg(2);

        assertEquals(1, publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().size());
    }

    @Test
    public void givenInFlightQueueNotEmpty_whenAckInFlightMsg_thenPublishedInFlightQueueIsDecremented() {
        publishedInFlightCtx.getPublishedInFlightMsgQueue().add(1);

        publishedInFlightCtx.ackInFlightMsg(1);

        assertEquals(0, publishedInFlightCtx.getPublishedInFlightMsgQueue().size());
    }

    @Test
    public void givenInFlightLimitStillReached_whenProcessDelayedMsg_thenSkipProcessing() {
        publishedInFlightCtx.getPublishedInFlightMsgCounter().set(10);
        publishedInFlightCtx.getDelayedMsgCounter().set(10);

        boolean result = publishedInFlightCtx.processMsg(1);

        assertEquals(10, publishedInFlightCtx.getDelayedMsgCounter().get());

        assertFalse(result);
    }

    @Test
    public void givenNoDelayedMessages_whenProcessDelayedMsg_thenRemoveClientFromDelayedProcessing() {
        publishedInFlightCtx.getPublishedInFlightMsgCounter().set(5);

        boolean result = publishedInFlightCtx.processMsg(1);

        verify(flowControlService, times(1)).removeFromMap("test");

        assertFalse(result);
    }

    @Test
    public void givenOneExpiredDelayedMsg_whenProcessDelayedMsg_thenSkipMsgAndRemoveClientFromDelayedProcessing() {
        publishedInFlightCtx.getPublishedInFlightMsgCounter().set(5);

        publishedInFlightCtx.getDelayedMsgQueue().add(new MqttPubMsgWithCreatedTime(null, System.currentTimeMillis() - 5000));
        publishedInFlightCtx.getDelayedMsgCounter().incrementAndGet();

        boolean result = publishedInFlightCtx.processMsg(1000);

        verify(flowControlService, times(1)).removeFromMap("test");
        assertEquals(0, publishedInFlightCtx.getDelayedMsgCounter().get());
        assertFalse(result);
    }

    @Test
    public void givenOneDelayedMsg_whenProcessDelayedMsg_thenSendMsgThroughChannelSuccessful() {
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(clientSessionCtx.getChannel()).thenReturn(channelHandlerContext);

        publishedInFlightCtx.getPublishedInFlightMsgCounter().set(5);

        publishedInFlightCtx.getDelayedMsgQueue().add(new MqttPubMsgWithCreatedTime(newAtLeastOnceMqttPubMsg(1), System.currentTimeMillis() - 5000));
        publishedInFlightCtx.getDelayedMsgCounter().incrementAndGet();

        boolean result = publishedInFlightCtx.processMsg(10000);

        verify(clientSessionCtx, times(1)).getChannel();
        assertEquals(6, publishedInFlightCtx.getPublishedInFlightMsgCounter().get());
        assertEquals(0, publishedInFlightCtx.getDelayedMsgCounter().get());
        assertTrue(result);
    }

    @Test
    public void givenAddedInfLightMessagesCase1_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));

        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(2);
        publishedInFlightCtx.ackInFlightMsg(3);
        publishedInFlightCtx.ackInFlightMsg(4);
        publishedInFlightCtx.ackInFlightMsg(5);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase2_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));

        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(2);
        publishedInFlightCtx.ackInFlightMsg(3);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase3_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(6));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(7));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(8));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(9));

        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(2);
        publishedInFlightCtx.ackInFlightMsg(5);
        publishedInFlightCtx.ackInFlightMsg(7);
        publishedInFlightCtx.ackInFlightMsg(6);
        publishedInFlightCtx.ackInFlightMsg(3);
        publishedInFlightCtx.ackInFlightMsg(4);
        publishedInFlightCtx.ackInFlightMsg(9);
        publishedInFlightCtx.ackInFlightMsg(8);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase4_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));

        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(2);
        publishedInFlightCtx.ackInFlightMsg(5);
        publishedInFlightCtx.ackInFlightMsg(7);
        publishedInFlightCtx.ackInFlightMsg(6);
        publishedInFlightCtx.ackInFlightMsg(3);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase5_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));

        publishedInFlightCtx.ackInFlightMsg(5);
        publishedInFlightCtx.ackInFlightMsg(4);
        publishedInFlightCtx.ackInFlightMsg(3);
        publishedInFlightCtx.ackInFlightMsg(2);
        publishedInFlightCtx.ackInFlightMsg(1);
        publishedInFlightCtx.ackInFlightMsg(1);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase6_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.ackInFlightMsg(1);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.ackInFlightMsg(2);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.ackInFlightMsg(3);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.ackInFlightMsg(4);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));
        publishedInFlightCtx.ackInFlightMsg(5);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase7_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.ackInFlightMsg(5);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.ackInFlightMsg(4);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.ackInFlightMsg(7);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.ackInFlightMsg(2);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));
        publishedInFlightCtx.ackInFlightMsg(6);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(6));
        publishedInFlightCtx.ackInFlightMsg(3);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(7));
        publishedInFlightCtx.ackInFlightMsg(9);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(8));
        publishedInFlightCtx.ackInFlightMsg(8);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(9));
        publishedInFlightCtx.ackInFlightMsg(1);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase8_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.ackInFlightMsg(5);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.ackInFlightMsg(4);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.ackInFlightMsg(7);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.ackInFlightMsg(1);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));
        publishedInFlightCtx.ackInFlightMsg(6);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(6));
        publishedInFlightCtx.ackInFlightMsg(3);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(7));
        publishedInFlightCtx.ackInFlightMsg(9);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(8));
        publishedInFlightCtx.ackInFlightMsg(8);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(9));
        publishedInFlightCtx.ackInFlightMsg(2);

        assertTrue(publishedInFlightCtx.getPublishedInFlightMsgQueue().isEmpty());
        assertTrue(publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().isEmpty());
    }

    @Test
    public void givenAddedInfLightMessagesCase9_whenReceivedAcknowledgements_thenSuccess() {
        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(1));
        publishedInFlightCtx.ackInFlightMsg(5);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(2));
        publishedInFlightCtx.ackInFlightMsg(4);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(3));
        publishedInFlightCtx.ackInFlightMsg(7);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(4));
        publishedInFlightCtx.ackInFlightMsg(1);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(5));
        publishedInFlightCtx.ackInFlightMsg(6);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(6));
        publishedInFlightCtx.ackInFlightMsg(3);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(7));
        publishedInFlightCtx.ackInFlightMsg(9);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(8));
        publishedInFlightCtx.ackInFlightMsg(8);

        publishedInFlightCtx.addInFlightMsg(newAtLeastOnceMqttPubMsg(9));
//        We have not received ack for msgId=2
//        publishedInFlightCtx.ackInFlightMsg(2);

        assertEquals(8, publishedInFlightCtx.getPublishedInFlightMsgQueue().size());
        assertEquals(7, publishedInFlightCtx.getReceivedAckMsgInWrongOrderQueue().size());
    }

    private MqttPublishMessage newAtLeastOnceMqttPubMsg(int packetId) {
        return mqttMessageCreator.newAtLeastOnceMqttPubMsg(packetId);
    }

    private MqttPublishMessage newAtMostOnceMqttPubMsg() {
        return mqttMessageCreator.newAtMostOnceMqttPubMsg();
    }

}
