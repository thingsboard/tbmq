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