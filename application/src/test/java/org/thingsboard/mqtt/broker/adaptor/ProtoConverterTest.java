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
package org.thingsboard.mqtt.broker.adaptor;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientConnectInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventDetailsProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientSubscriptionsProto;
import org.thingsboard.mqtt.broker.gen.queue.DevicePublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderEventProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderTypeProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttPropertiesProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.RetainHandling;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.SessionInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.TopicSubscriptionProto;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.IpAddressBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.UsernameBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientConnectInfo;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.data.SourcedSubscriptions;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        SessionInfoProto sessionInfoProto = ProtoConverter.convertToSessionInfoProto(sessionInfo);
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
                new ClientTopicSubscription("topic1", 0, "name1"),
                new ClientTopicSubscription("topic2", 1),
                new ClientTopicSubscription(
                        "topic3",
                        2,
                        null,
                        new SubscriptionOptions(
                                true,
                                true,
                                SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE),
                        1)
        );

        ClientSubscriptionsProto clientSubscriptionsProto = ProtoConverter.convertToClientSubscriptionsProto(input);

        List<TopicSubscriptionProto> sortedList = clientSubscriptionsProto.getSubscriptionsList().stream().sorted(Comparator.comparing(TopicSubscriptionProto::getTopic)).toList();

        TopicSubscriptionProto subscription0 = sortedList.get(0);
        assertEquals("topic1", subscription0.getTopic());
        assertEquals(0, subscription0.getQos());
        assertEquals("name1", subscription0.getShareName());
        assertFalse(subscription0.hasSubscriptionId());
        assertFalse(subscription0.getOptions().getNoLocal());
        assertFalse(subscription0.getOptions().getRetainAsPublish());
        assertEquals(RetainHandling.SEND, subscription0.getOptions().getRetainHandling());

        TopicSubscriptionProto subscription2 = sortedList.get(2);
        assertEquals("topic3", subscription2.getTopic());
        assertEquals(2, subscription2.getQos());
        assertFalse(subscription2.hasShareName());
        assertTrue(subscription2.hasSubscriptionId());
        assertEquals(1, subscription2.getSubscriptionId());
        assertTrue(subscription2.getOptions().getNoLocal());
        assertTrue(subscription2.getOptions().getRetainAsPublish());
        assertEquals(RetainHandling.DONT_SEND, subscription2.getOptions().getRetainHandling());

        TopicSubscriptionProto topicSubscriptionProto = clientSubscriptionsProto.getSubscriptionsList()
                .stream()
                .filter(tsp -> tsp.getShareName().isEmpty())
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscriptionProto);

        SourcedSubscriptions sourcedSubscriptions = ProtoConverter.convertProtoToClientSubscriptions(clientSubscriptionsProto);
        Set<TopicSubscription> output = sourcedSubscriptions.getSubscriptions();

        TopicSubscription topicSubscription = output
                .stream()
                .filter(ts -> ts.getShareName() == null)
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscription);

        assertEquals(input, output);
        assertEquals(input.size(), output.size());
        assertEquals(3, input.size());

        List<TopicSubscription> topicSubscriptionList = output.stream().sorted(Comparator.comparing(TopicSubscription::getTopicFilter)).toList();

        TopicSubscription topicSubscription0 = topicSubscriptionList.get(0);
        assertEquals("topic1", topicSubscription0.getTopicFilter());
        assertEquals(-1, topicSubscription0.getSubscriptionId());

        TopicSubscription topicSubscription2 = topicSubscriptionList.get(2);
        assertEquals("topic3", topicSubscription2.getTopicFilter());
        assertEquals(1, topicSubscription2.getSubscriptionId());
        assertTrue(topicSubscription2.getOptions().isNoLocal());
        assertTrue(topicSubscription2.getOptions().isRetainAsPublish());
        assertEquals(SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE, topicSubscription2.getOptions().getRetainHandling());
    }

    @Test
    public void givenMqttPropertiesProtoAndEmptyProps_whenAddFromProtoToMqttProperties_thenPropsNotEmpty() {
        MqttPropertiesProto build = MqttPropertiesProto
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

        MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(properties);

        assertNotNull(mqttPropsProtoBuilder);
        MqttPropertiesProto proto = mqttPropsProtoBuilder.build();
        assertEquals("test", proto.getContentType());
        assertEquals(1, proto.getPayloadFormatIndicator());
    }

    @Test
    public void givenMqttPropertiesWithResponseTopicAndCorrelationData_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.BinaryProperty(BrokerConstants.CORRELATION_DATA_PROP_ID, BrokerConstants.DUMMY_PAYLOAD));
        properties.add(new MqttProperties.StringProperty(BrokerConstants.RESPONSE_TOPIC_PROP_ID, "test/"));

        MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(properties);

        assertNotNull(mqttPropsProtoBuilder);
        MqttPropertiesProto proto = mqttPropsProtoBuilder.build();
        assertEquals("test/", proto.getResponseTopic());
        assertEquals("test", proto.getCorrelationData().toString(StandardCharsets.UTF_8));
    }

    @Test
    public void givenMqttPropertiesWithPayloadFormatIndicatorAndNoContentType_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID, 1));

        MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(properties);

        assertNotNull(mqttPropsProtoBuilder);
        MqttPropertiesProto proto = mqttPropsProtoBuilder.build();
        assertFalse(proto.hasContentType());
        assertEquals(1, proto.getPayloadFormatIndicator());
    }

    @Test
    public void givenMqttPropertiesWithNoPayloadFormatIndicatorAndNoContentType_whenGetMqttPropsProtoBuilder_thenGetExpectedResult() {
        MqttPropertiesProto.Builder mqttPropsProtoBuilder = ProtoConverter.getMqttPropsProtoBuilder(new MqttProperties());
        assertNull(mqttPropsProtoBuilder);
    }

    @Test
    public void givenRetainedMsg_whenConvertToRetainedMsgProto_thenGetExpectedRetainedMsgProto() {
        MqttProperties properties = new MqttProperties();
        RetainedMsg retainedMsg = new RetainedMsg("t", "p".getBytes(StandardCharsets.UTF_8), 1, properties);

        RetainedMsgProto retainedMsgProto = ProtoConverter.convertToRetainedMsgProto(retainedMsg);
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

        RetainedMsgProto retainedMsgProto = ProtoConverter.convertToRetainedMsgProto(retainedMsg);
        assertEquals(1, retainedMsgProto.getQos());
        assertEquals("t", retainedMsgProto.getTopic());
        assertTrue(retainedMsgProto.hasMqttProperties());
        assertEquals(1, retainedMsgProto.getUserPropertiesCount());
    }

    @Test
    public void givenRetainedMsgProto_whenConvertProtoToRetainedMsg_thenGetExpectedRetainedMsg() {
        RetainedMsgProto proto = RetainedMsgProto
                .newBuilder()
                .setMqttProperties(MqttPropertiesProto.newBuilder().setPayloadFormatIndicator(1).build())
                .setQos(1)
                .setPayload(ByteString.copyFromUtf8("payload"))
                .setCreatedTime(System.currentTimeMillis())
                .setTopic("topic")
                .build();

        RetainedMsg retainedMsg = ProtoConverter.convertProtoToRetainedMsg(proto);
        assertEquals("topic", retainedMsg.getTopic());
        assertEquals(1, retainedMsg.getQos());
        assertNotNull(retainedMsg.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNull(retainedMsg.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
    }

    @Test
    public void givenDevicePubMsgProto_whenExecuteProtoToDevicePublishMsg_thenGetExpectedDevicePubMsg() {
        DevicePublishMsgProto proto = DevicePublishMsgProto
                .newBuilder()
                .setMqttProperties(MqttPropertiesProto.newBuilder().setContentType("testCT").build())
                .setQos(1)
                .setPacketType("PUBLISH")
                .setPayload(ByteString.copyFromUtf8("payload"))
                .setClientId("clientId")
                .setTopicName("topic")
                .build();

        DevicePublishMsg devicePublishMsg = ProtoConverter.protoToDevicePublishMsg(proto);
        assertEquals("topic", devicePublishMsg.getTopicName());
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
                .time(123213L)
                .payload("p".getBytes(StandardCharsets.UTF_8))
                .packetType(PersistedPacketType.PUBREL)
                .clientId("cli")
                .topicName("topic")
                .packetId(124)
                .build();

        DevicePublishMsgProto devicePublishMsgProto = ProtoConverter.toDevicePublishMsgProto(devicePublishMsg);
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
                .qos(2)
                .properties(properties)
                .payload("p".getBytes(StandardCharsets.UTF_8))
                .build();
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishMsgProto(sessionInfo, publishMsg);

        assertEquals(2, publishMsgProto.getQos());
        assertEquals("topic", publishMsgProto.getTopicName());
        assertTrue(publishMsgProto.hasMqttProperties());
        assertEquals(0, publishMsgProto.getMqttProperties().getPayloadFormatIndicator());
        assertEquals(2, publishMsgProto.getUserPropertiesCount());
    }

    @Test
    public void givenPubMsgProto_whenExecuteConvertToPublishMsg_thenGetExpectedPubMsg() {
        PublishMsgProto proto = PublishMsgProto.newBuilder()
                .setTopicName("t")
                .setPayload(ByteString.copyFromUtf8("p"))
                .setMqttProperties(MqttPropertiesProto.newBuilder().setPayloadFormatIndicator(1).build())
                .build();
        PublishMsg publishMsg = ProtoConverter.convertToPublishMsg(proto, 1, 1, false, -1);

        assertEquals("t", publishMsg.getTopicName());
        assertEquals(1, publishMsg.getPacketId());
        assertEquals(1, publishMsg.getQos());
        assertNotNull(publishMsg.getProperties().getProperty(BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID));
        assertNull(publishMsg.getProperties().getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID));
        assertNull(publishMsg.getProperties().getProperty(BrokerConstants.SUBSCRIPTION_IDENTIFIER_PROP_ID));
    }

    @Test
    public void givenPubMsgAndSubscriptionWithSameQosAndFalseRetainAsPublish_whenProcessUpdatePublishMsg_thenReturnSameMsg() {
        Subscription subscription = new Subscription("test/topic", 1, ClientSessionInfo.builder().build());
        PublishMsgProto beforePublishMsgProto = PublishMsgProto.newBuilder().setQos(1).setRetain(false).build();

        PublishMsgProto afterPublishMsgProto = ProtoConverter.updatePublishMsg(subscription, beforePublishMsgProto);

        Assert.assertEquals(beforePublishMsgProto, afterPublishMsgProto);
    }

    @Test
    public void givenPubMsgAndSubscriptionWithDifferentQos_whenProcessUpdatePublishMsg_thenReturnUpdatedMsgWithMinQos() {
        Subscription subscription = new Subscription("test/topic", 1, ClientSessionInfo.builder().build());
        PublishMsgProto beforePublishMsgProto = PublishMsgProto.newBuilder().setQos(2).setRetain(true).build();

        PublishMsgProto afterPublishMsgProto = ProtoConverter.updatePublishMsg(subscription, beforePublishMsgProto);

        Assert.assertNotEquals(beforePublishMsgProto, afterPublishMsgProto);
        Assert.assertEquals(1, afterPublishMsgProto.getQos());
        Assert.assertFalse(afterPublishMsgProto.getRetain());
    }

    @Test
    public void givenPubMsgAndSubscriptionWithSameQosAndRetainAsPublish_whenProcessUpdatePublishMsg_thenReturnSameMsg() {
        Subscription subscription = new Subscription(
                "test/topic",
                2,
                ClientSessionInfo.builder().build(),
                null,
                new SubscriptionOptions(
                        false,
                        true,
                        SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE));

        PublishMsgProto beforePublishMsgProto = PublishMsgProto.newBuilder().setQos(2).setRetain(true).build();

        PublishMsgProto afterPublishMsgProto = ProtoConverter.updatePublishMsg(subscription, beforePublishMsgProto);

        Assert.assertEquals(beforePublishMsgProto, afterPublishMsgProto);
        Assert.assertEquals(2, afterPublishMsgProto.getQos());
        Assert.assertTrue(afterPublishMsgProto.getRetain());
    }

    @Test
    public void givenPubMsgAndSubsWithIds_whenProcessUpdatePublishMsg_thenReturnUpdatedMsgWithIds() {
        Subscription subscription = new Subscription("test/topic", 1, Lists.newArrayList(1, 2, 3));
        PublishMsgProto beforePublishMsgProto = PublishMsgProto.newBuilder().setQos(2).setRetain(false).build();

        PublishMsgProto afterPublishMsgProto = ProtoConverter.updatePublishMsg(subscription, beforePublishMsgProto);

        Assert.assertEquals(1, afterPublishMsgProto.getQos());
        Assert.assertEquals(List.of(1, 2, 3), afterPublishMsgProto.getMqttProperties().getSubscriptionIdsList());
    }

    @Test
    public void givenPubMsgWithPropsAndSubsWithIds_whenProcessUpdatePublishMsg_thenReturnUpdatedMsgWithIds() {
        Subscription subscription = new Subscription("test/topic", 1, Lists.newArrayList(1, 2, 3));
        MqttPropertiesProto mqttPropertiesProto = MqttPropertiesProto.newBuilder().setContentType("test").build();
        PublishMsgProto beforePublishMsgProto = PublishMsgProto.newBuilder().setQos(2).setRetain(false).setMqttProperties(mqttPropertiesProto).build();

        PublishMsgProto afterPublishMsgProto = ProtoConverter.updatePublishMsg(subscription, beforePublishMsgProto);

        Assert.assertEquals(1, afterPublishMsgProto.getQos());
        Assert.assertEquals(List.of(1, 2, 3), afterPublishMsgProto.getMqttProperties().getSubscriptionIdsList());
        Assert.assertEquals("test", afterPublishMsgProto.getMqttProperties().getContentType());
    }

    @Test
    public void givenPubMsgWithPropsAndSubsWithIds_whenProcessCreateReceiverPublishMsg_thenReturnUpdatedMsgWithIds() {
        Subscription subscription = new Subscription("test/topic", 1, Lists.newArrayList(1, 2, 3));
        MqttPropertiesProto mqttPropertiesProto = MqttPropertiesProto.newBuilder().setContentType("test").build();
        PublishMsgProto beforePublishMsgProto = PublishMsgProto.newBuilder().setQos(2).setPacketId(1).setRetain(false).setMqttProperties(mqttPropertiesProto).build();

        PublishMsgProto afterPublishMsgProto = ProtoConverter.createReceiverPublishMsg(subscription, beforePublishMsgProto);

        Assert.assertEquals(1, afterPublishMsgProto.getQos());
        Assert.assertEquals(0, afterPublishMsgProto.getPacketId());
        Assert.assertEquals(List.of(1, 2, 3), afterPublishMsgProto.getMqttProperties().getSubscriptionIdsList());
        Assert.assertEquals("test", afterPublishMsgProto.getMqttProperties().getContentType());
    }

    private final long expirationTime = System.currentTimeMillis() + 60000;
    private final String description = "Test block entry";

    @Test
    public void givenClientIdBlockedClient_whenConvertedToProtoAndBack_thenFieldsArePreserved() {
        ClientIdBlockedClient client = new ClientIdBlockedClient(expirationTime, description, "client123");

        BlockedClientProto proto = ProtoConverter.convertToBlockedClientProto(client);
        BlockedClient converted = ProtoConverter.convertProtoToBlockedClient(proto);

        assertInstanceOf(ClientIdBlockedClient.class, converted);
        assertEquals(client.getValue(), converted.getValue());
        assertEquals(client.getExpirationTime(), converted.getExpirationTime());
        assertEquals(client.getDescription(), converted.getDescription());
    }

    @Test
    public void givenUsernameBlockedClient_whenConvertedToProtoAndBack_thenFieldsArePreserved() {
        UsernameBlockedClient client = new UsernameBlockedClient(expirationTime, description, "user");

        BlockedClientProto proto = ProtoConverter.convertToBlockedClientProto(client);
        BlockedClient converted = ProtoConverter.convertProtoToBlockedClient(proto);

        assertInstanceOf(UsernameBlockedClient.class, converted);
        assertEquals(client.getValue(), converted.getValue());
        assertEquals(client.getExpirationTime(), converted.getExpirationTime());
        assertEquals(client.getDescription(), converted.getDescription());
    }

    @Test
    public void givenIpAddressBlockedClient_whenConvertedToProtoAndBack_thenFieldsArePreserved() {
        IpAddressBlockedClient client = new IpAddressBlockedClient(expirationTime, description, "192.168.1.1");

        BlockedClientProto proto = ProtoConverter.convertToBlockedClientProto(client);
        BlockedClient converted = ProtoConverter.convertProtoToBlockedClient(proto);

        assertInstanceOf(IpAddressBlockedClient.class, converted);
        assertEquals(client.getValue(), converted.getValue());
        assertEquals(client.getExpirationTime(), converted.getExpirationTime());
        assertEquals(client.getDescription(), converted.getDescription());
    }

    @Test
    public void givenRegexBlockedClient_whenConvertedToProtoAndBack_thenFieldsAndMatchTargetArePreserved() {
        RegexBlockedClient client = new RegexBlockedClient(expirationTime, description, "^test_.*", RegexMatchTarget.BY_CLIENT_ID);

        BlockedClientProto proto = ProtoConverter.convertToBlockedClientProto(client);
        BlockedClient converted = ProtoConverter.convertProtoToBlockedClient(proto);

        assertInstanceOf(RegexBlockedClient.class, converted);
        assertEquals(client.getValue(), converted.getValue());
        assertEquals(client.getExpirationTime(), converted.getExpirationTime());
        assertEquals(client.getDescription(), converted.getDescription());
        assertEquals(client.getRegexMatchTarget(), converted.getRegexMatchTarget());
    }

    @Test
    public void givenNullDescription_whenConvertedToProtoAndBack_thenDescriptionIsNull() {
        ClientIdBlockedClient client = new ClientIdBlockedClient(expirationTime, null, "client456");

        BlockedClientProto proto = ProtoConverter.convertToBlockedClientProto(client);
        assertFalse(proto.hasDescription());

        BlockedClient converted = ProtoConverter.convertProtoToBlockedClient(proto);
        assertNull(converted.getDescription());
    }

    @Test
    public void givenMqttAuthSettings_whenConvertToProtoAndBack_thenPrioritiesPreserved() {
        List<MqttAuthProviderType> originalPriorities = List.of(
                MqttAuthProviderType.MQTT_BASIC,
                MqttAuthProviderType.JWT,
                MqttAuthProviderType.X_509
        );

        MqttAuthSettings settings = new MqttAuthSettings();
        settings.setPriorities(originalPriorities);

        InternodeNotificationProto proto = ProtoConverter.toMqttAuthSettingUpdateProto(settings);

        assertTrue(proto.hasMqttAuthSettingsProto());

        // Extract back the priorities from proto
        List<MqttAuthProviderTypeProto> protoList = proto.getMqttAuthSettingsProto().getPrioritiesList();
        List<MqttAuthProviderType> result = ProtoConverter.fromMqttAuthPriorities(protoList);

        // Validate
        assertEquals(originalPriorities, result);
    }

    @Test
    public void givenMqttAuthSettingsWithNullPriorities_whenConvertToProto_thenValidateNullHandledProperly() {
        MqttAuthSettings settings = new MqttAuthSettings();
        settings.setPriorities(null);

        InternodeNotificationProto proto = ProtoConverter.toMqttAuthSettingUpdateProto(settings);

        assertTrue(proto.hasMqttAuthSettingsProto());

        // Validate
        MqttAuthSettingsProto mqttAuthSettingsProto = proto.getMqttAuthSettingsProto();
        assertTrue(mqttAuthSettingsProto.getPrioritiesList().isEmpty());
    }

    @Test
    public void givenNullProtoPrioritiesList_whenConvertBack_thenValidateNullHandledProperly() {
        List<MqttAuthProviderType> mqttAuthProviderTypes = ProtoConverter.fromMqttAuthPriorities(null);

        assertNotNull(mqttAuthProviderTypes);
        assertTrue(mqttAuthProviderTypes.isEmpty());
    }

    @Test
    public void givenClientId_whenConvertToClientSessionStatsCleanupProto_thenClientIdIsPreserved() {
        String clientId = "device-001";

        InternodeNotificationProto proto = ProtoConverter.toClientSessionStatsCleanupProto(clientId);

        assertTrue(proto.hasClientSessionStatsCleanupProto());
        assertEquals(clientId, proto.getClientSessionStatsCleanupProto().getClientId());
    }

    @Test
    public void givenMqttAuthProvider_whenConvertToUpdatedEvent_thenFieldsAreCorrect() {
        MqttAuthProvider provider = MqttAuthProvider.defaultJwtAuthProvider(true);

        InternodeNotificationProto proto = ProtoConverter.toMqttAuthProviderUpdatedEvent(provider);

        assertTrue(proto.hasMqttAuthProviderProto());

        var authProto = proto.getMqttAuthProviderProto();
        assertEquals(MqttAuthProviderEventProto.PROVIDER_UPDATED, authProto.getEventType());
        assertEquals(MqttAuthProviderType.JWT.getProtoNumber(), authProto.getProviderType().getNumber());
        assertTrue(authProto.getEnabled());

        String configuration = authProto.getConfiguration();
        JwtMqttAuthProviderConfiguration jwtMqttAuthProviderConfiguration =
                assertDoesNotThrow(() -> JacksonUtil.fromString(configuration, JwtMqttAuthProviderConfiguration.class));
        assertEquals(provider.getConfiguration(), jwtMqttAuthProviderConfiguration);
    }

    @Test
    public void givenAuthProviderType_whenConvertToEnabledEvent_thenEventFieldsAreCorrect() {
        MqttAuthProviderType type = MqttAuthProviderType.JWT;

        InternodeNotificationProto proto = ProtoConverter.toMqttAuthProviderEnabledEvent(type);

        assertTrue(proto.hasMqttAuthProviderProto());

        var providerProto = proto.getMqttAuthProviderProto();
        assertEquals(MqttAuthProviderEventProto.PROVIDER_ENABLED, providerProto.getEventType());
        assertEquals(type.getProtoNumber(), providerProto.getProviderType().getNumber());
        assertFalse(providerProto.hasEnabled());
        assertFalse(providerProto.hasConfiguration());
    }

    @Test
    public void givenAuthProviderType_whenConvertToDisableEvent_thenEventFieldsAreCorrect() {
        MqttAuthProviderType type = MqttAuthProviderType.SCRAM;

        InternodeNotificationProto proto = ProtoConverter.toMqttAuthProviderDisabledEvent(type);

        assertTrue(proto.hasMqttAuthProviderProto());

        var providerProto = proto.getMqttAuthProviderProto();
        assertEquals(MqttAuthProviderEventProto.PROVIDER_DISABLED, providerProto.getEventType());
        assertEquals(type.getProtoNumber(), providerProto.getProviderType().getNumber());
        assertFalse(providerProto.hasEnabled());
        assertFalse(providerProto.hasConfiguration());
    }

    @Test
    public void givenClientConnectInfoWithBothFields_whenConvertToProto_thenBothPresent() {
        ClientConnectInfo connectInfo = new ClientConnectInfo("5.0", "user:pass");

        ClientConnectInfoProto proto = ProtoConverter.toClientConnectInfoProto(connectInfo);

        assertTrue(proto.hasMqttVersion());
        assertEquals("5.0", proto.getMqttVersion());
        assertTrue(proto.hasAuthDetails());
        assertEquals("user:pass", proto.getAuthDetails());
    }

    @Test
    public void givenClientConnectInfoWithNullFields_whenConvertToProto_thenFieldsAbsent() {
        ClientConnectInfo connectInfo = new ClientConnectInfo(null, null);

        ClientConnectInfoProto proto = ProtoConverter.toClientConnectInfoProto(connectInfo);

        assertFalse(proto.hasMqttVersion());
        assertFalse(proto.hasAuthDetails());
    }

    @Test
    public void givenClientConnectInfoWithEmptyStrings_whenConvertToProto_thenFieldsAbsent() {
        ClientConnectInfo connectInfo = new ClientConnectInfo("", "");

        ClientConnectInfoProto proto = ProtoConverter.toClientConnectInfoProto(connectInfo);

        assertFalse(proto.hasMqttVersion());
        assertFalse(proto.hasAuthDetails());
    }

    @Test
    public void givenClientConnectInfoWithOnlyMqttVersion_whenConvertToProto_thenOnlyMqttVersionPresent() {
        ClientConnectInfo connectInfo = new ClientConnectInfo("3.1.1", null);

        ClientConnectInfoProto proto = ProtoConverter.toClientConnectInfoProto(connectInfo);

        assertTrue(proto.hasMqttVersion());
        assertEquals("3.1.1", proto.getMqttVersion());
        assertFalse(proto.hasAuthDetails());
    }

    @Test
    public void givenClientConnectInfoWithOnlyAuthDetails_whenConvertToProto_thenOnlyAuthDetailsPresent() {
        ClientConnectInfo connectInfo = new ClientConnectInfo(null, "jwt");

        ClientConnectInfoProto proto = ProtoConverter.toClientConnectInfoProto(connectInfo);

        assertFalse(proto.hasMqttVersion());
        assertTrue(proto.hasAuthDetails());
        assertEquals("jwt", proto.getAuthDetails());
    }

    @Test
    public void givenEventDetailsWithConnectInfoProto_whenConvertToClientConnectInfo_thenFieldsRestored() {
        ClientConnectInfoProto connectInfoProto = ClientConnectInfoProto.newBuilder()
                .setMqttVersion("5.0")
                .setAuthDetails("user:pass")
                .build();

        ClientSessionEventDetailsProto detailsProto = ClientSessionEventDetailsProto.newBuilder()
                .setClientConnectInfo(connectInfoProto)
                .build();

        ClientConnectInfo connectInfo = ProtoConverter.getClientConnectInfo(detailsProto);

        assertNotNull(connectInfo);
        assertEquals("5.0", connectInfo.getMqttVersion());
        assertEquals("user:pass", connectInfo.getAuthDetails());
    }

    @Test
    public void givenEventDetailsWithEmptyConnectInfoProto_whenConvertToClientConnectInfo_thenNulls() {
        ClientSessionEventDetailsProto detailsProto = ClientSessionEventDetailsProto.newBuilder()
                .setClientConnectInfo(ClientConnectInfoProto.getDefaultInstance())
                .build();

        ClientConnectInfo connectInfo = ProtoConverter.getClientConnectInfo(detailsProto);

        assertNotNull(connectInfo);
        assertNull(connectInfo.getMqttVersion());
        assertNull(connectInfo.getAuthDetails());
    }

    @Test
    public void givenEventDetailsWithOnlyMqttVersionProto_whenConvertToClientConnectInfo_thenOnlyMqttVersionRestored() {
        ClientConnectInfoProto connectInfoProto = ClientConnectInfoProto.newBuilder()
                .setMqttVersion("3.1.1")
                .build();

        ClientSessionEventDetailsProto detailsProto = ClientSessionEventDetailsProto.newBuilder()
                .setClientConnectInfo(connectInfoProto)
                .build();

        ClientConnectInfo connectInfo = ProtoConverter.getClientConnectInfo(detailsProto);

        assertEquals("3.1.1", connectInfo.getMqttVersion());
        assertNull(connectInfo.getAuthDetails());
    }

    @Test
    public void givenEventDetailsWithOnlyAuthDetailsProto_whenConvertToClientConnectInfo_thenOnlyAuthDetailsRestored() {
        ClientConnectInfoProto connectInfoProto = ClientConnectInfoProto.newBuilder()
                .setAuthDetails("jwt")
                .build();

        ClientSessionEventDetailsProto detailsProto = ClientSessionEventDetailsProto.newBuilder()
                .setClientConnectInfo(connectInfoProto)
                .build();

        ClientConnectInfo connectInfo = ProtoConverter.getClientConnectInfo(detailsProto);

        assertNull(connectInfo.getMqttVersion());
        assertEquals("jwt", connectInfo.getAuthDetails());
    }

    @Test
    public void givenClientConnectInfo_whenConvertToProtoAndBack_thenRoundTripOk() {
        ClientConnectInfo input = new ClientConnectInfo("5.0", "user:pass");

        ClientConnectInfoProto proto = ProtoConverter.toClientConnectInfoProto(input);
        ClientSessionEventDetailsProto detailsProto = ClientSessionEventDetailsProto.newBuilder()
                .setClientConnectInfo(proto)
                .build();

        ClientConnectInfo output = ProtoConverter.getClientConnectInfo(detailsProto);

        assertNotNull(output);
        assertEquals(input.getMqttVersion(), output.getMqttVersion());
        assertEquals(input.getAuthDetails(), output.getAuthDetails());
    }

}
