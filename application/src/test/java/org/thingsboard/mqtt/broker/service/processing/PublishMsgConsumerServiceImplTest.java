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
package org.thingsboard.mqtt.broker.service.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// PackProcessingContext, PackProcessingResult, PublishMsgWithId, MsgDispatcherService, AckStrategyFactory,
// SubmitStrategyFactory are all in this same package (org.thingsboard.mqtt.broker.service.processing) — no import needed.
@RunWith(MockitoJUnitRunner.class)
public class PublishMsgConsumerServiceImplTest {

    private TbMessageStatsReportClient reportClient;
    private PublishMsgConsumerServiceImpl consumer;

    @Before
    public void setUp() {
        reportClient = mock(TbMessageStatsReportClient.class);
        consumer = new PublishMsgConsumerServiceImpl(
                mock(MsgDispatcherService.class),
                mock(PublishMsgQueueFactory.class),
                mock(AckStrategyFactory.class),
                mock(SubmitStrategyFactory.class),
                mock(ServiceInfoProvider.class),
                mock(StatsManager.class),
                reportClient);
    }

    private PackProcessingResult resultWith(int pending, int failed) {
        ConcurrentMap<UUID, PublishMsgWithId> pendingMap = new ConcurrentHashMap<>();
        for (int i = 0; i < pending + failed; i++) {
            pendingMap.put(UUID.randomUUID(), mock(PublishMsgWithId.class));
        }
        PackProcessingContext ctx = new PackProcessingContext(pendingMap);
        int i = 0;
        for (UUID id : pendingMap.keySet()) {
            if (i++ < failed) {
                ctx.onFailure(id);
            }
        }
        return new PackProcessingResult(ctx);
    }

    @Test
    public void givenPendingAndFailed_whenReportDroppedOnGiveUp_thenReportsSum() {
        consumer.reportDroppedMsgsOnGiveUp(resultWith(2, 3));

        verify(reportClient, times(1)).reportDroppedMsgs(5);
    }

    @Test
    public void givenCleanPack_whenReportDroppedOnGiveUp_thenReportsNothing() {
        consumer.reportDroppedMsgsOnGiveUp(resultWith(0, 0));

        verify(reportClient, never()).reportDroppedMsgs(anyInt());
    }
}
