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
package org.thingsboard.mqtt.broker.standalone.integration;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.integration.AuthorizationAction;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class NoOpIntegrationStandInsTest {

    @Test
    @SuppressWarnings("unchecked")
    void theQueuePublisherFailsTheCopy() {
        PublishMsgCallback callback = mock(PublishMsgCallback.class);
        TbProtoQueueMsg<PublishIntegrationMsgProto> msg = mock(TbProtoQueueMsg.class);

        new NoOpIntegrationMsgQueuePublisher().sendMsg("integration-id", msg, callback);

        verify(callback).onFailure(isA(UnsupportedOperationException.class));
        verifyNoMoreInteractions(callback);
        verifyNoInteractions(msg);
    }

    @Test
    void theLifecycleEventPublisherDoesNothing() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        SessionInfo sessionInfo = mock(SessionInfo.class);
        NoOpIntegrationLifecycleEventPublisher publisher = new NoOpIntegrationLifecycleEventPublisher();

        publisher.publishConnected(ctx);
        publisher.publishDisconnected(ctx, DisconnectReasonType.ON_DISCONNECT_MSG);
        publisher.publishSubscribed(ctx, List.of());
        publisher.publishUnsubscribed(ctx, List.of());
        publisher.publishAuthenticationFailed(ctx, "client", "reason");
        publisher.publishAuthorizationDenied(ctx, AuthorizationAction.PUBLISH, "topic");
        publisher.publishConnectionFailed(ctx, sessionInfo, "reason");

        verifyNoInteractions(ctx, sessionInfo);
    }

}
