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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionStatsCleanupProto;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ClientSessionStatsCleanupProcessorImplTest {

    @Mock
    private TbMessageStatsReportClient tbMessageStatsReportClient;

    private ClientSessionStatsCleanupProcessorImpl processor;

    @Before
    public void setUp() {
        processor = new ClientSessionStatsCleanupProcessorImpl(tbMessageStatsReportClient);
    }

    @Test
    public void testProcessClientSessionStatsCleanup_CallsRemoveClient() {
        // given
        String clientId = "tbmq-dev";
        ClientSessionStatsCleanupProto proto = ClientSessionStatsCleanupProto.newBuilder()
                .setClientId(clientId)
                .build();

        // when
        processor.processClientSessionStatsCleanup(proto);

        // then
        verify(tbMessageStatsReportClient).removeClient(clientId);
    }
}