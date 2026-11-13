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
package org.thingsboard.mqtt.broker.dao.service;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Checks every {@link DeviceMsgService} implementation must pass, the Valkey one and the store-backed one. A plain
 * class and not a test base class, because the Valkey test extends a Spring base class. Each check runs on its own
 * client id and removes what it stored.
 */
public class DeviceMsgServiceContract {

    private static final long TIMEOUT_SEC = 10;

    private final DeviceMsgService service;
    private final int limit;

    /**
     * @param limit the persisted messages limit the service was configured with
     */
    public DeviceMsgServiceContract(DeviceMsgService service, int limit) {
        this.service = service;
        this.limit = limit;
    }

    /**
     * Runs every check and throws one {@link AssertionError} listing the failed ones.
     */
    public void runAll() {
        Map<String, Runnable> checks = new LinkedHashMap<>();
        checks.put("saveThenFindInOrderWithIdsFromOne", this::saveThenFindInOrderWithIdsFromOne);
        checks.put("secondSaveReturnsThePreviousPacketId", this::secondSaveReturnsThePreviousPacketId);
        checks.put("moreSavedThanTheLimitKeepsTheNewest", this::moreSavedThanTheLimitKeepsTheNewest);
        checks.put("removeOneLeavesTheOthers", this::removeOneLeavesTheOthers);
        checks.put("removeAMissingOneReturnsNormally", this::removeAMissingOneReturnsNormally);
        checks.put("updatePacketReceivedTurnsOneEntryIntoPubRel", this::updatePacketReceivedTurnsOneEntryIntoPubRel);
        checks.put("removeAllEmptiesTheQueueAndResetsThePacketId", this::removeAllEmptiesTheQueueAndResetsThePacketId);
        checks.put("lastPacketIdOnAnEmptyQueue", this::lastPacketIdOnAnEmptyQueue);
        checks.put("mqttPropertiesRoundTrip", this::mqttPropertiesRoundTrip);

        List<String> failures = new ArrayList<>();
        checks.forEach((name, check) -> {
            try {
                check.run();
            } catch (Throwable t) {
                failures.add(name + ": " + t);
            } finally {
                cleanUp(clientId(name));
            }
        });
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " of " + checks.size() + " contract checks failed:\n" + String.join("\n", failures));
        }
    }

    private void saveThenFindInOrderWithIdsFromOne() {
        String clientId = clientId("saveThenFindInOrderWithIdsFromOne");
        List<DevicePublishMsg> saved = msgs(clientId, 5);

        assertThat(join(service.saveAndReturnPreviousPacketId(clientId, saved, false))).isZero();

        List<DevicePublishMsg> found = join(service.findPersistedMessages(clientId));
        assertThat(found).extracting(DevicePublishMsg::getPacketId).containsExactly(1, 2, 3, 4, 5);
        assertThat(found).extracting(DevicePublishMsg::getPacketType).containsOnly(PersistedPacketType.PUBLISH);
        assertThat(found).extracting(DevicePublishMsg::getClientId).containsOnly(clientId);
        assertThat(found).extracting(DevicePublishMsg::getTopicName).containsExactlyElementsOf(saved.stream().map(DevicePublishMsg::getTopicName).toList());
        assertThat(found).extracting(m -> new String(m.getPayload(), StandardCharsets.UTF_8)).containsExactly("p0", "p1", "p2", "p3", "p4");
        assertThat(found).extracting(DevicePublishMsg::getQos).containsOnly(1);
        assertThat(found).extracting(DevicePublishMsg::isRetained).containsOnly(false);
    }

    private void secondSaveReturnsThePreviousPacketId() {
        String clientId = clientId("secondSaveReturnsThePreviousPacketId");

        assertThat(join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 3), false))).isZero();
        assertThat(join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 2), false))).isEqualTo(3);

        assertThat(join(service.getLastPacketId(clientId))).isEqualTo(5);
        assertThat(join(service.findPersistedMessages(clientId))).extracting(DevicePublishMsg::getPacketId).containsExactly(1, 2, 3, 4, 5);
    }

    private void moreSavedThanTheLimitKeepsTheNewest() {
        String clientId = clientId("moreSavedThanTheLimitKeepsTheNewest");
        int saved = limit + 5;

        join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, saved), false));

        assertThat(join(service.getLastPacketId(clientId))).isEqualTo(saved);
        assertThat(join(service.findPersistedMessages(clientId))).extracting(DevicePublishMsg::getPacketId)
                .containsExactlyElementsOf(IntStream.rangeClosed(6, saved).boxed().toList());
    }

    private void removeOneLeavesTheOthers() {
        String clientId = clientId("removeOneLeavesTheOthers");
        join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 3), false));

        assertThat(join(service.removePersistedMessage(clientId, 2))).isEqualTo("OK");

        assertThat(join(service.findPersistedMessages(clientId))).extracting(DevicePublishMsg::getPacketId).containsExactly(1, 3);
    }

    private void removeAMissingOneReturnsNormally() {
        String clientId = clientId("removeAMissingOneReturnsNormally");

        assertThat(join(service.findPersistedMessages(clientId))).isEmpty();
        join(service.removePersistedMessage(clientId, 1));    // must complete without an exception

        join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 2), false));
        join(service.removePersistedMessage(clientId, 9));
        assertThat(join(service.findPersistedMessages(clientId))).extracting(DevicePublishMsg::getPacketId).containsExactly(1, 2);
    }

    private void updatePacketReceivedTurnsOneEntryIntoPubRel() {
        String clientId = clientId("updatePacketReceivedTurnsOneEntryIntoPubRel");
        join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 3), false));

        assertThat(join(service.updatePacketReceived(clientId, 2))).isEqualTo("OK");

        List<DevicePublishMsg> found = join(service.findPersistedMessages(clientId));
        assertThat(found).extracting(DevicePublishMsg::getPacketId).containsExactly(1, 2, 3);
        assertThat(found).extracting(DevicePublishMsg::getPacketType)
                .containsExactly(PersistedPacketType.PUBLISH, PersistedPacketType.PUBREL, PersistedPacketType.PUBLISH);
    }

    private void removeAllEmptiesTheQueueAndResetsThePacketId() {
        String clientId = clientId("removeAllEmptiesTheQueueAndResetsThePacketId");
        join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 3), false));

        join(service.removePersistedMessages(clientId));

        assertThat(join(service.findPersistedMessages(clientId))).isEmpty();
        assertThat(join(service.getLastPacketId(clientId))).isZero();
        assertThat(join(service.saveAndReturnPreviousPacketId(clientId, msgs(clientId, 1), false))).isZero();
    }

    private void lastPacketIdOnAnEmptyQueue() {
        String clientId = clientId("lastPacketIdOnAnEmptyQueue");

        assertThat(join(service.getLastPacketId(clientId))).isZero();
        assertThat(join(service.saveLastPacketId(clientId, 5))).isEqualTo("OK");
        assertThat(join(service.getLastPacketId(clientId))).isEqualTo(5);
        assertThat(join(service.removeLastPacketId(clientId))).isEqualTo(1L);
        assertThat(join(service.getLastPacketId(clientId))).isZero();
    }

    private void mqttPropertiesRoundTrip() {
        String clientId = clientId("mqttPropertiesRoundTrip");
        DevicePublishMsg msg = msg(clientId, "t/props", "props", new MqttProperties());
        msg.setUserProperties(new UserProperties(List.of(
                new UserProperties.StringPair("k1", "v1"),
                new UserProperties.StringPair("k2", "v2"),
                new UserProperties.StringPair("k1", "v3"))));
        msg.setContentType("application/json");
        msg.setResponseTopic("reply/topic");
        msg.setCorrelationData(new byte[]{1, 2, 3});
        msg.setPayloadFormatIndicator(1);
        msg.setSubscriptionIds(List.of(3, 7));

        join(service.saveAndReturnPreviousPacketId(clientId, List.of(msg), false));

        DevicePublishMsg found = join(service.findPersistedMessages(clientId)).get(0);
        assertThat(found.getUserProperties().getValues()).extracting(UserProperties.StringPair::getKey, UserProperties.StringPair::getValue)
                .containsExactly(tuple("k1", "v1"), tuple("k2", "v2"), tuple("k1", "v3"));
        assertThat(found.getContentType()).isEqualTo("application/json");
        assertThat(found.getResponseTopic()).isEqualTo("reply/topic");
        assertThat(found.getCorrelationData()).containsExactly(1, 2, 3);
        assertThat(found.getPayloadFormatIndicator()).isEqualTo(1);
        assertThat(found.getSubscriptionIds()).containsExactly(3, 7);
    }

    private void cleanUp(String clientId) {
        try {
            join(service.removePersistedMessages(clientId));
            join(service.removeLastPacketId(clientId));
        } catch (Throwable ignored) {
            // a failed clean-up must not hide the check's own outcome
        }
    }

    private static String clientId(String check) {
        return "contract-" + check;
    }

    private static List<DevicePublishMsg> msgs(String clientId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> msg(clientId, "t/" + UUID.randomUUID(), "p" + i, new MqttProperties()))
                .toList();
    }

    private static DevicePublishMsg msg(String clientId, String topic, String payload, MqttProperties properties) {
        return new DevicePublishMsg(clientId, topic, System.currentTimeMillis(), 1, BrokerConstants.BLANK_PACKET_ID,
                PersistedPacketType.PUBLISH, payload.getBytes(StandardCharsets.UTF_8), properties, false);
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("The call did not complete normally: " + e, e);
        }
    }

}
