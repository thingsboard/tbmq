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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.provider.DevicePersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.ClientIdMessagesPack;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceMsgPersistenceSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceMsgProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DevicePackProcessingContext;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DevicePackProcessingResult;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DeviceMsgQueueConsumerImplTest {

    private TbMessageStatsReportClient reportClient;
    private DeviceMsgQueueConsumerImpl consumer;

    @Before
    public void setUp() {
        reportClient = mock(TbMessageStatsReportClient.class);
        consumer = new DeviceMsgQueueConsumerImpl(
                mock(DevicePersistenceMsgQueueFactory.class),
                mock(DeviceMsgAcknowledgeStrategyFactory.class),
                mock(DeviceMsgPersistenceSubmitStrategyFactory.class),
                mock(DeviceMsgProcessor.class),
                mock(StatsManager.class),
                mock(ServiceInfoProvider.class),
                mock(ClientLogger.class),
                reportClient);
    }

    private ClientIdMessagesPack packWith(String clientId, int messageCount) {
        List<DevicePublishMsg> msgs = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            msgs.add(mock(DevicePublishMsg.class));
        }
        return new ClientIdMessagesPack(clientId, msgs);
    }

    // Builds a result whose failedMap holds the given packs and whose pendingMap holds `pendingCount`
    // single-message packs (which must NOT be counted).
    private DevicePackProcessingResult resultWith(List<ClientIdMessagesPack> failedPacks, int pendingCount) {
        ConcurrentMap<String, ClientIdMessagesPack> pending = new ConcurrentHashMap<>();
        for (ClientIdMessagesPack pack : failedPacks) {
            pending.put(pack.clientId(), pack);
        }
        for (int i = 0; i < pendingCount; i++) {
            String cid = "pending-" + i;
            pending.put(cid, packWith(cid, 1));
        }
        DevicePackProcessingContext ctx = new DevicePackProcessingContext(pending);
        for (ClientIdMessagesPack pack : failedPacks) {
            ctx.onFailure(pack.clientId());
        }
        return new DevicePackProcessingResult(ctx);
    }

    @Test
    public void givenFailedPacks_whenReportDroppedOnGiveUp_thenReportsSumOfMessages() {
        DevicePackProcessingResult result = resultWith(
                List.of(packWith("c1", 2), packWith("c2", 3)), 4);

        consumer.reportDroppedMsgsOnGiveUp(result);

        verify(reportClient, times(1)).reportDroppedMsgs(5); // 2+3; pendingMap (4) excluded
    }

    @Test
    public void givenNoFailures_whenReportDroppedOnGiveUp_thenReportsNothing() {
        DevicePackProcessingResult result = resultWith(List.of(), 3);

        consumer.reportDroppedMsgsOnGiveUp(result);

        verify(reportClient, never()).reportDroppedMsgs(anyInt());
    }
}
