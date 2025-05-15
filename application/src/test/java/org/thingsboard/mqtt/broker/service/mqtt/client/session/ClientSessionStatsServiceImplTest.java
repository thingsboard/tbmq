package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ClientSessionStatsServiceImplTest {

    @Mock
    private InternodeNotificationsService notificationsService;

    private ClientSessionStatsServiceImpl service;

    @Before
    public void setUp() {
        service = new ClientSessionStatsServiceImpl(notificationsService);
    }

    @Test
    public void testBroadcastCleanupClientSessionStatsRequest() {
        // given
        String clientId = "client-007";
        InternodeNotificationProto expectedProto = ProtoConverter.toClientSessionStatsCleanupProto(clientId);

        // when
        service.broadcastCleanupClientSessionStatsRequest(clientId);

        // then
        verify(notificationsService).broadcast(expectedProto);
    }
}
