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
package org.thingsboard.mqtt.broker.adaptor;

import com.google.protobuf.ByteString;
import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class ProtoConverterTest {

    @Test
    public void givenSessionInfo_whenCheckForPersistence_thenOk() {
        SessionInfo sessionInfo = newSessionInfo(true, 5);
        Assert.assertTrue(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(false, 5);
        Assert.assertTrue(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(true, 0);
        Assert.assertFalse(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(false, 0);
        Assert.assertTrue(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(true, -1);
        Assert.assertFalse(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(false, -1);
        Assert.assertTrue(sessionInfo.isPersistent());

        sessionInfo = newSessionInfo(true, 0);
        Assert.assertTrue(sessionInfo.isCleanSession());
        sessionInfo = newSessionInfo(true, -1);
        Assert.assertTrue(sessionInfo.isCleanSession());
        sessionInfo = newSessionInfo(true, 5);
        Assert.assertFalse(sessionInfo.isCleanSession());

        sessionInfo = newSessionInfo(false, 0);
        Assert.assertTrue(sessionInfo.isNotCleanSession());
        sessionInfo = newSessionInfo(false, -1);
        Assert.assertTrue(sessionInfo.isNotCleanSession());
        sessionInfo = newSessionInfo(false, 5);
        Assert.assertFalse(sessionInfo.isNotCleanSession());
    }

    @Test
    public void givenSessionInfo_whenConvertToProtoAndBackWithSessionExpiryIntervalNull_thenOk() {
        SessionInfo sessionInfoConverted = convertSessionInfo(-1);
        Assert.assertEquals(-1, sessionInfoConverted.getSessionExpiryInterval());
    }

    @Test
    public void givenSessionInfo_whenConvertToProtoAndBackWithSessionExpiryIntervalNotNull_thenOk() {
        SessionInfo sessionInfoConverted = convertSessionInfo(5);
        Assert.assertEquals(5, sessionInfoConverted.getSessionExpiryInterval());
    }

    private static SessionInfo convertSessionInfo(int sessionExpiryInterval) {
        SessionInfo sessionInfo = newSessionInfo(true, sessionExpiryInterval);

        QueueProtos.SessionInfoProto sessionInfoProto = ProtoConverter.convertToSessionInfoProto(sessionInfo);
        SessionInfo sessionInfoConverted = ProtoConverter.convertToSessionInfo(sessionInfoProto);

        Assert.assertEquals(sessionInfo, sessionInfoConverted);
        return sessionInfoConverted;
    }

    private static SessionInfo newSessionInfo(boolean cleanStart, int sessionExpiryInterval) {
        return SessionInfo.builder()
                .serviceId("serviceId")
                .sessionId(UUID.randomUUID())
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .clientInfo(ClientInfo.builder()
                        .clientId("clientId")
                        .type(ClientType.DEVICE)
                        .clientIpAdr(BrokerConstants.LOCAL_ADR)
                        .build())
                .connectionInfo(ConnectionInfo.builder()
                        .connectedAt(10)
                        .disconnectedAt(20)
                        .keepAlive(100)
                        .build())
                .build();
    }

    @Test
    public void givenTopicSubscriptions_whenConvertToProtoAndBack_thenOk() {
        Set<TopicSubscription> input = Set.of(
                new TopicSubscription("topic1", 0, "name1"),
                new TopicSubscription("topic2", 1)
        );

        QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto = ProtoConverter.convertToClientSubscriptionsProto(input);

        QueueProtos.TopicSubscriptionProto topicSubscriptionProto = clientSubscriptionsProto.getSubscriptionsList()
                .stream()
                .filter(tsp -> tsp.getShareName().isEmpty())
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscriptionProto);

        Set<TopicSubscription> output = ProtoConverter.convertProtoToClientSubscriptions(clientSubscriptionsProto);

        TopicSubscription topicSubscription = output
                .stream()
                .filter(ts -> ts.getShareName() == null)
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscription);

        assertEquals(input, output);
    }

    @Test
    public void givenMqttPropertiesProtoAndEmptyProps_whenAddFromProtoToMqttProperties_thenPropsNotEmpty() {
        QueueProtos.MqttPropertiesProto build = QueueProtos.MqttPropertiesProto
                .newBuilder()
                .setContentType("testCT")
                .setPayloadFormatIndicator(1)
                .setCorrelationData(ByteString.copyFromUtf8("test"))
                .setResponseTopic("test/")
                .build();
        MqttProperties properties = new MqttProperties();

        assertNull(properties.getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNull(properties.getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
        assertNull(properties.getProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID));
        assertNull(properties.getProperty(BrokerConstants.CORRELATION_DATA_PROP_ID));

        ProtoConverter.addFromProtoToMqttProperties(build, properties);

        assertNotNull(properties.getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNotNull(properties.getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
        assertNotNull(properties.getProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID));
        assertNotNull(properties.getProperty(BrokerConstants.CORRELATION_DATA_PROP_ID));
    }

    @Test
    public void givenMqttPropertiesWithPayloadFormatIndicatorAndContentType_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, 1));
        properties.add(new MqttProperties.StringProperty(BrokerConstants.CONTENT_TYPE_PROP_ID, "test"));

        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(properties);

        assertNotNull(mqttPropsProtoBuilder);
        QueueProtos.MqttPropertiesProto proto = mqttPropsProtoBuilder.build();
        assertEquals("test", proto.getContentType());
        assertEquals(1, proto.getPayloadFormatIndicator());
    }

    @Test
    public void givenMqttPropertiesWithResponseTopicAndCorrelationData_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.BinaryProperty(BrokerConstants.CORRELATION_DATA_PROP_ID, "test".getBytes(StandardCharsets.UTF_8)));
        properties.add(new MqttProperties.StringProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID, "test/"));

        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(properties);

        assertNotNull(mqttPropsProtoBuilder);
        QueueProtos.MqttPropertiesProto proto = mqttPropsProtoBuilder.build();
        assertEquals("test/", proto.getResponseTopic());
        assertEquals("test", proto.getCorrelationData().toString(StandardCharsets.UTF_8));
    }

    @Test
    public void givenMqttPropertiesWithPayloadFormatIndicatorAndNoContentType_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, 1));

        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(properties);

        assertNotNull(mqttPropsProtoBuilder);
        QueueProtos.MqttPropertiesProto proto = mqttPropsProtoBuilder.build();
        assertFalse(proto.hasContentType());
        assertEquals(1, proto.getPayloadFormatIndicator());
    }

    @Test
    public void givenMqttPropertiesWithNoPayloadFormatIndicatorAndNoContentType_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(new MqttProperties());
        assertNull(mqttPropsProtoBuilder);
    }

    @Test
    public void givenRetainedMsg_whenConvertToRetainedMsgProto_thenGetExpectedRetainedMsgProto() {
        MqttProperties properties = new MqttProperties();
        RetainedMsg retainedMsg = new RetainedMsg("t", "p".getBytes(StandardCharsets.UTF_8), 1, properties);

        QueueProtos.RetainedMsgProto retainedMsgProto = ProtoConverter.convertToRetainedMsgProto(retainedMsg);
        assertEquals(1, retainedMsgProto.getQos());
        assertEquals("t", retainedMsgProto.getTopic());
        assertFalse(retainedMsgProto.hasMqttProperties());
        assertEquals(0, retainedMsgProto.getUserPropertiesCount());
    }

    @Test
    public void givenRetainedMsgWithPresentMqttProperties_whenConvertToRetainedMsgProto_thenGetExpectedRetainedMsgProto() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, 0));
        properties.add(new MqttProperties.UserProperty("key", "value"));
        RetainedMsg retainedMsg = new RetainedMsg("t", "p".getBytes(StandardCharsets.UTF_8), 1, properties);

        QueueProtos.RetainedMsgProto retainedMsgProto = ProtoConverter.convertToRetainedMsgProto(retainedMsg);
        assertEquals(1, retainedMsgProto.getQos());
        assertEquals("t", retainedMsgProto.getTopic());
        assertTrue(retainedMsgProto.hasMqttProperties());
        assertEquals(1, retainedMsgProto.getUserPropertiesCount());
    }

    @Test
    public void givenRetainedMsgProto_whenConvertProtoToRetainedMsg_thenGetExpectedRetainedMsg() {
        QueueProtos.RetainedMsgProto proto = QueueProtos.RetainedMsgProto
                .newBuilder()
                .setMqttProperties(QueueProtos.MqttPropertiesProto.newBuilder().setPayloadFormatIndicator(1).build())
                .setQos(1)
                .setPayload(ByteString.copyFromUtf8("payload"))
                .setCreatedTime(System.currentTimeMillis())
                .setTopic("topic")
                .build();

        RetainedMsg retainedMsg = ProtoConverter.convertProtoToRetainedMsg(proto);
        assertEquals("topic", retainedMsg.getTopic());
        assertEquals(1, retainedMsg.getQosLevel());
        assertNotNull(retainedMsg.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNull(retainedMsg.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
    }

    @Test
    public void givenDevicePubMsgProto_whenExecuteProtoToDevicePublishMsg_thenGetExpectedDevicePubMsg() {
        QueueProtos.DevicePublishMsgProto proto = QueueProtos.DevicePublishMsgProto
                .newBuilder()
                .setMqttProperties(QueueProtos.MqttPropertiesProto.newBuilder().setContentType("testCT").build())
                .setQos(1)
                .setPacketType("PUBLISH")
                .setPayload(ByteString.copyFromUtf8("payload"))
                .setClientId("clientId")
                .setTopicName("topic")
                .build();

        DevicePublishMsg devicePublishMsg = ProtoConverter.protoToDevicePublishMsg(proto);
        assertEquals("topic", devicePublishMsg.getTopic());
        assertEquals(1, devicePublishMsg.getQos());
        assertEquals(PersistedPacketType.PUBLISH, devicePublishMsg.getPacketType());
        assertNull(devicePublishMsg.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNotNull(devicePublishMsg.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
    }

    @Test
    public void givenDevicePubMsg_whenExecuteToDevicePublishMsgProto_thenGetExpectedDevicePubMsgProto() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, 0));
        properties.add(new MqttProperties.UserProperty("key", "value"));

        DevicePublishMsg devicePublishMsg = DevicePublishMsg.builder()
                .properties(properties)
                .qos(0)
                .serialNumber(123L)
                .time(123213L)
                .payload("p".getBytes(StandardCharsets.UTF_8))
                .packetType(PersistedPacketType.PUBREL)
                .clientId("cli")
                .topic("topic")
                .packetId(124)
                .build();

        QueueProtos.DevicePublishMsgProto devicePublishMsgProto = ProtoConverter.toDevicePublishMsgProto(devicePublishMsg);
        assertEquals(0, devicePublishMsgProto.getQos());
        assertEquals("topic", devicePublishMsgProto.getTopicName());
        assertTrue(devicePublishMsgProto.hasMqttProperties());
        assertEquals(0, devicePublishMsgProto.getMqttProperties().getPayloadFormatIndicator());
        assertEquals(1, devicePublishMsgProto.getUserPropertiesCount());
    }

    @Test
    public void givenPubMsg_whenExecuteConvertToPublishMsgProto_thenGetExpectedPubMsgProto() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, 0));
        properties.add(new MqttProperties.UserProperty("key", "value"));
        properties.add(new MqttProperties.UserProperty("foo", "bar"));

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().clientId("cli").build()).build();
        PublishMsg publishMsg = PublishMsg.builder()
                .topicName("topic")
                .packetId(1)
                .qosLevel(2)
                .properties(properties)
                .payload("p".getBytes(StandardCharsets.UTF_8))
                .build();
        QueueProtos.PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishMsgProto(sessionInfo, publishMsg);

        assertEquals(2, publishMsgProto.getQos());
        assertEquals("topic", publishMsgProto.getTopicName());
        assertTrue(publishMsgProto.hasMqttProperties());
        assertEquals(0, publishMsgProto.getMqttProperties().getPayloadFormatIndicator());
        assertEquals(2, publishMsgProto.getUserPropertiesCount());
    }

    @Test
    public void givenPubMsgProto_whenExecuteConvertToPublishMsg_thenGetExpectedPubMsg() {
        QueueProtos.PublishMsgProto proto = QueueProtos.PublishMsgProto.newBuilder()
                .setTopicName("t")
                .setPayload(ByteString.copyFromUtf8("p"))
                .setMqttProperties(QueueProtos.MqttPropertiesProto.newBuilder().setPayloadFormatIndicator(1).build())
                .build();
        PublishMsg publishMsg = ProtoConverter.convertToPublishMsg(proto, 1, 1, false);

        assertEquals("t", publishMsg.getTopicName());
        assertEquals(1, publishMsg.getPacketId());
        assertEquals(1, publishMsg.getQosLevel());
        assertNotNull(publishMsg.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNull(publishMsg.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
    }

}
