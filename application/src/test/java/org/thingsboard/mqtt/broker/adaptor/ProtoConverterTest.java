/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class ProtoConverterTest {

    @Test
    public void givenSessionInfo_whenConvertToProtoAndBackWithSessionExpiryIntervalNull_thenOk() {
        SessionInfo sessionInfoConverted = getSessionInfo(null);
        Assert.assertNull(sessionInfoConverted.getSessionExpiryInterval());
    }

    @Test
    public void givenSessionInfo_whenConvertToProtoAndBackWithSessionExpiryIntervalNotNull_thenOk() {
        SessionInfo sessionInfoConverted = getSessionInfo(5);
        Assert.assertNotNull(sessionInfoConverted.getSessionExpiryInterval());
    }

    private static SessionInfo getSessionInfo(Integer sessionExpiryInterval) {
        SessionInfo sessionInfo = SessionInfo.builder()
                .serviceId("serviceId")
                .sessionId(UUID.randomUUID())
                .persistent(false)
                .sessionExpiryInterval(sessionExpiryInterval)
                .clientInfo(ClientInfo.builder()
                        .clientId("clientId")
                        .type(ClientType.DEVICE)
                        .build())
                .connectionInfo(ConnectionInfo.builder()
                        .connectedAt(10)
                        .disconnectedAt(20)
                        .keepAlive(100)
                        .build())
                .build();

        QueueProtos.SessionInfoProto sessionInfoProto = ProtoConverter.convertToSessionInfoProto(sessionInfo);
        SessionInfo sessionInfoConverted = ProtoConverter.convertToSessionInfo(sessionInfoProto);

        Assert.assertEquals(sessionInfo, sessionInfoConverted);
        return sessionInfoConverted;
    }
}