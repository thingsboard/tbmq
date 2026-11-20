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
package org.thingsboard.mqtt.broker.lite.spec;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.service.mqtt.client.cleanup.ClientSessionCleanUpServiceImpl;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The packet ids of the QoS 2 PUBLISH packets a persistent session has received and not yet released with a PUBREL
 * survive a broker restart ([MQTT-4.3.3-2], [MQTT-4.3.3-5]): the broker stores them with the session and, when the
 * client reconnects, sends a PUBREC for each, so the client can finish the flow without the message being delivered
 * twice. The context is removed with the session. The publisher is a raw client that withholds its PUBREL.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = Qos2SessionContextLiteTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class Qos2SessionContextLiteTest extends AbstractLiteSpecCase {

    private static final int PACKET_ID = 7;
    private static final int SESSION_EXPIRY_INTERVAL_SEC = 1;

    @Autowired
    private GenericClientSessionCtxService genericClientSessionCtxService;
    @Autowired
    private DeviceMsgQueueStore deviceMsgQueueStore;
    @Autowired
    private RocksDbStore rocksDbStore;
    @Autowired
    private ClientSessionCleanUpServiceImpl clientSessionCleanUpService;

    @Test
    public void mqtt_4_3_3_qos2PacketIdsAwaitingPubrelSurviveARestart() throws Exception {
        String topic = "spec/qos2ctx/restart";
        String subscriberId = "qos2ctx-restart-sub";
        String publisherId = "qos2ctx-restart-pub";
        RawMqttClient subscriber = persistentSubscriber(subscriberId, topic, 1);
        RawMqttClient publisher = connectPersistent(publisherId);

        publisher.publish(topic, 2, PACKET_ID, "once", null);
        assertThat(publisher.expect(MqttMessageType.PUBREC, PACKET_ID).reasonCode()).isEqualTo(SUCCESS);
        // no PUBREL
        RawMqttClient.Packet delivered = nextPublish(subscriber);
        assertThat(delivered.payload()).isEqualTo("once");
        subscriber.pubAck(delivered.packetId());
        await("queue of " + subscriberId + " empty").atMost(TIMEOUT).until(() -> deviceMsgQueueStore.info(subscriberId).count() == 0);

        // the restart stores the packet ids of the sessions that are open and closes their connections
        SpringRestarter.getInstance().restart();
        publisher.close();
        subscriber.close();

        Optional<GenericClientSessionCtx> stored = genericClientSessionCtxService.findGenericClientSessionCtx(publisherId);
        assertThat(stored).isPresent();
        assertThat(stored.get().getQos2PublishPacketIds()).containsExactly(PACKET_ID);
        // in the embedded store, under the client id
        assertThat(rocksDbStore.get(StoreColumnFamily.SESSION_CTX, publisherId.getBytes(StandardCharsets.UTF_8))).isNotNull();

        RawMqttClient reconnected = connectPersistent(publisherId);
        assertThat(reconnected.sessionPresent()).isTrue();
        // the broker asks for the PUBREL again
        assertThat(reconnected.expect(MqttMessageType.PUBREC, PACKET_ID).reasonCode()).isEqualTo(SUCCESS);
        reconnected.pubRel(PACKET_ID);
        assertThat(reconnected.expect(MqttMessageType.PUBCOMP, PACKET_ID).reasonCode()).isEqualTo(SUCCESS);

        // the message was delivered before the restart and is not delivered again
        RawMqttClient resubscribed = connectPersistent(subscriberId);
        assertThat(resubscribed.sessionPresent()).isTrue();
        assertThat(resubscribed.poll(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    public void theContextIsRemovedWithTheSession() throws Exception {
        String topic = "spec/qos2ctx/expiry";
        String clientId = "qos2ctx-expiry";
        RawMqttClient client = connectPersistent(clientId, SESSION_EXPIRY_INTERVAL_SEC);

        client.publish(topic, 2, PACKET_ID, "withheld", null);
        assertThat(client.expect(MqttMessageType.PUBREC, PACKET_ID).reasonCode()).isEqualTo(SUCCESS);
        // no PUBREL; the disconnect of the persistent session stores the packet id
        disconnect(client, clientId);
        await("context of " + clientId + " stored").atMost(TIMEOUT)
                .until(() -> genericClientSessionCtxService.findGenericClientSessionCtx(clientId).isPresent());
        // in the embedded store, under the client id
        byte[] key = clientId.getBytes(StandardCharsets.UTF_8);
        assertThat(rocksDbStore.get(StoreColumnFamily.SESSION_CTX, key)).isNotNull();

        // the scheduled cleanup removes the session once its expiry interval has passed; it may do so before this wait
        await("session of " + clientId + " expired or removed").atMost(TIMEOUT).until(() -> {
            ClientSessionInfo session = clientSessionCache.getClientSessionInfo(clientId);
            return session == null || session.isExpired(System.currentTimeMillis(), ClientSessionInfo.NO_TTL);
        });
        clientSessionCleanUpService.cleanUp();

        await("context of " + clientId + " removed").atMost(TIMEOUT)
                .until(() -> genericClientSessionCtxService.findGenericClientSessionCtx(clientId).isEmpty());
        assertThat(rocksDbStore.get(StoreColumnFamily.SESSION_CTX, key)).isNull();
        assertThat(clientSessionCache.getClientSessionInfo(clientId)).isNull();
    }

}
