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
package org.thingsboard.mqtt.broker.session;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TopicAliasCtxTest {

    TopicAliasCtx topicAliasCtx;
    int minTopicNameLengthForAliasReplacement = 10;

    @Test
    public void givenPubMsgWithoutTopicAlias_whenGetTopicNameByAlias_thenNoTopicReturned() {
        topicAliasCtx = new TopicAliasCtx(true, 5);

        MqttProperties properties = new MqttProperties();
        PublishMsg publishMsg = PublishMsg.builder().properties(properties).build();
        String topicNameByAlias = topicAliasCtx.getTopicNameByAlias(publishMsg);

        Assert.assertNull(topicNameByAlias);
    }

    @Test(expected = MqttException.class)
    public void givenPubMsgWithTopicAliasAndAbsentTopicAndMapping_whenGetTopicNameByAlias_thenThrownException() {
        topicAliasCtx = new TopicAliasCtx(true, 5);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID, 1));
        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName(BrokerConstants.EMPTY_STR)
                .properties(properties)
                .build();

        topicAliasCtx.getTopicNameByAlias(publishMsg);
    }

    @Test
    public void givenPubMsgWithTopicAliasAndMappingAndAbsentTopic_whenGetTopicNameByAlias_thenReturnTopicFromMapping() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(Map.of(1, "topic123")), null);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID, 1));
        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName(BrokerConstants.EMPTY_STR)
                .properties(properties)
                .build();

        String topicNameByAlias = topicAliasCtx.getTopicNameByAlias(publishMsg);
        Assert.assertEquals("topic123", topicNameByAlias);
    }

    @Test
    public void givenPubMsgWithTopicAliasAndTopicName_whenGetTopicNameByAlias_thenReturnTopicAndAddMapping() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(Map.of(1, "topic123")), null);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID, 2));
        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName("topic/qwerty")
                .properties(properties)
                .build();

        String topicNameByAlias = topicAliasCtx.getTopicNameByAlias(publishMsg);
        Assert.assertEquals("topic/qwerty", topicNameByAlias);

        String value = topicAliasCtx.getClientMappings().get(2);
        Assert.assertEquals("topic/qwerty", value);
        Assert.assertEquals(2, topicAliasCtx.getClientMappings().size());
    }

    @Test
    public void givenPubMsgWithTopicAliasAndTopicNameAndPresentMapping_whenGetTopicNameByAlias_thenReturnTopicAndUpdateMapping() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(Map.of(1, "topic123")), null);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID, 1));
        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName("topic/qwerty")
                .properties(properties)
                .build();

        String topicNameByAlias = topicAliasCtx.getTopicNameByAlias(publishMsg);
        Assert.assertEquals("topic/qwerty", topicNameByAlias);

        String value = topicAliasCtx.getClientMappings().get(1);
        Assert.assertEquals("topic/qwerty", value);
        Assert.assertEquals(1, topicAliasCtx.getClientMappings().size());
    }

    @Test
    public void givenPubMsgWithSmallTopic_whenCreatePublishMsgUsingTopicAlias_thenReturnSamePubMsg() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(Map.of(1, "topic123")), null);

        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName("topic/1")
                .properties(new MqttProperties())
                .build();

        PublishMsg publishMsgUsingTopicAlias = topicAliasCtx.createPublishMsgUsingTopicAlias(publishMsg, minTopicNameLengthForAliasReplacement);

        Assert.assertEquals(publishMsg, publishMsgUsingTopicAlias);
    }

    @Test
    public void givenPubMsgWithTopicAndMaxAllowedAliases_whenCreatePublishMsgUsingTopicAlias_thenReturnSamePubMsg() {
        topicAliasCtx = new TopicAliasCtx(true, 1, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("topic123", 1)));

        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName("topic/qwerty")
                .properties(new MqttProperties())
                .build();

        PublishMsg publishMsgUsingTopicAlias = topicAliasCtx.createPublishMsgUsingTopicAlias(publishMsg, minTopicNameLengthForAliasReplacement);

        Assert.assertEquals(publishMsg, publishMsgUsingTopicAlias);
    }

    @Test
    public void givenPubMsgWithTopic_whenCreatePublishMsgUsingTopicAlias_thenReturnUpdatedPubMsg() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("topic123", 1)));

        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName("topic/qwerty")
                .properties(new MqttProperties())
                .build();

        PublishMsg publishMsgUsingTopicAlias = topicAliasCtx.createPublishMsgUsingTopicAlias(publishMsg, minTopicNameLengthForAliasReplacement);

        Assert.assertEquals("topic/qwerty", publishMsgUsingTopicAlias.getTopicName());
        int topicAlias = (int) publishMsgUsingTopicAlias.getProperties().getProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID).value();
        Assert.assertEquals(2, topicAlias);
        Assert.assertEquals(2, topicAliasCtx.getServerMappings().size());
    }

    @Test
    public void givenPubMsgWithTopicAndExisingMapping_whenCreatePublishMsgUsingTopicAlias_thenReturnUpdatedPubMsgWithEmptyTopic() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("topic123456", 1)));

        PublishMsg publishMsg = PublishMsg
                .builder()
                .topicName("topic123456")
                .properties(new MqttProperties())
                .build();

        PublishMsg publishMsgUsingTopicAlias = topicAliasCtx.createPublishMsgUsingTopicAlias(publishMsg, minTopicNameLengthForAliasReplacement);

        Assert.assertEquals(BrokerConstants.EMPTY_STR, publishMsgUsingTopicAlias.getTopicName());
        int topicAlias = (int) publishMsgUsingTopicAlias.getProperties().getProperty(BrokerConstants.TOPIC_ALIAS_PROP_ID).value();
        Assert.assertEquals(1, topicAlias);
        Assert.assertEquals(1, topicAliasCtx.getServerMappings().size());
    }

    @Test
    public void givenDisabledTopicAliasCtx_whenGetTopicAliasResult_thenReturnNull() {
        topicAliasCtx = new TopicAliasCtx(false, 5, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        TopicAliasResult topicAliasResult = topicAliasCtx.getTopicAliasResult(null, minTopicNameLengthForAliasReplacement);
        Assert.assertNull(topicAliasResult);
    }

    @Test
    public void givenPubMsgProtoWithSmallTopic_whenGetTopicAliasResult_thenReturnNull() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto.newBuilder()
                .setTopicName("123456789")
                .build();

        TopicAliasResult topicAliasResult = topicAliasCtx.getTopicAliasResult(publishMsgProto, minTopicNameLengthForAliasReplacement);
        Assert.assertNull(topicAliasResult);
    }

    @Test
    public void givenPubMsgProtoWithTopicAndMaxAllowedAliases_whenGetTopicAliasResult_thenReturnNull() {
        topicAliasCtx = new TopicAliasCtx(true, 1, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("qwerty", 1)));

        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto.newBuilder()
                .setTopicName("12345678900")
                .build();

        TopicAliasResult topicAliasResult = topicAliasCtx.getTopicAliasResult(publishMsgProto, minTopicNameLengthForAliasReplacement);
        Assert.assertNull(topicAliasResult);
    }

    @Test
    public void givenPubMsgProtoWithTopicAndExisingMapping_whenGetTopicAliasResult_thenReturnTopicAliasResultWithEmptyTopic() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("12345678900", 1)));

        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto.newBuilder()
                .setTopicName("12345678900")
                .build();

        TopicAliasResult topicAliasResult = topicAliasCtx.getTopicAliasResult(publishMsgProto, minTopicNameLengthForAliasReplacement);
        Assert.assertEquals(new TopicAliasResult(BrokerConstants.EMPTY_STR, 1), topicAliasResult);
        Assert.assertEquals(1, topicAliasCtx.getServerMappings().size());
    }

    @Test
    public void givenPubMsgProtoWithTopic_whenGetTopicAliasResult_thenReturnTopicAliasResultWithNewAlias() {
        topicAliasCtx = new TopicAliasCtx(true, 5, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("qwerty", 1)));

        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto.newBuilder()
                .setTopicName("12345678900")
                .build();

        TopicAliasResult topicAliasResult = topicAliasCtx.getTopicAliasResult(publishMsgProto, minTopicNameLengthForAliasReplacement);
        Assert.assertEquals(new TopicAliasResult("12345678900", 2), topicAliasResult);
        Assert.assertEquals(2, topicAliasCtx.getServerMappings().size());
    }

    @Test
    public void givenNoAliases_whenGetNextTopicAliasMaxTimes_thenReturnNewAliasesUntilZeroReturned() {
        topicAliasCtx = new TopicAliasCtx(true, 2);

        int topicAlias = topicAliasCtx.getNextTopicAlias("test/topic/1");
        Assert.assertEquals(1, topicAlias);
        topicAlias = topicAliasCtx.getNextTopicAlias("test/topic/2");
        Assert.assertEquals(2, topicAlias);
        topicAlias = topicAliasCtx.getNextTopicAlias("test/topic/3");
        Assert.assertEquals(0, topicAlias);
    }

    @Test
    public void givenMaxAliasesReached_whenGetNextTopicAlias_thenDoNotReturnNewAlias() {
        topicAliasCtx = new TopicAliasCtx(true, 2, new ConcurrentHashMap<>(Map.of(1, "1", 2, "2")), new ConcurrentHashMap<>());

        int topicAlias = topicAliasCtx.getNextTopicAlias("test/topic/1");
        Assert.assertEquals(0, topicAlias);
    }

    @Test
    public void givenMaxAliasesNotReached_whenGetNextTopicAlias_thenReturnNewAlias() {
        topicAliasCtx = new TopicAliasCtx(true, 2, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(Map.of("1", 1)));

        int topicAlias = topicAliasCtx.getNextTopicAlias("test/topic/1");
        Assert.assertEquals(2, topicAlias);
    }

    @Test
    public void givenAliasIsLessThanMaxAlias_whenValidateTopicAlias_thenSuccess() {
        topicAliasCtx = new TopicAliasCtx(true, 2);

        topicAliasCtx.validateTopicAlias(1);
    }

    @Test(expected = MqttException.class)
    public void givenAliasIsMoreThanMaxAlias_whenValidateTopicAlias_thenFailure() {
        topicAliasCtx = new TopicAliasCtx(true, 2);

        topicAliasCtx.validateTopicAlias(3);
    }

    @Test(expected = MqttException.class)
    public void givenAliasIsZero_whenValidateTopicAlias_thenFailure() {
        topicAliasCtx = new TopicAliasCtx(true, 2);

        topicAliasCtx.validateTopicAlias(0);
    }

}
