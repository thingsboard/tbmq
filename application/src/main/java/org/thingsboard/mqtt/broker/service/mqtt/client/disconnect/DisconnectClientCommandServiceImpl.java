/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.client.disconnect;

import com.google.common.util.concurrent.SettableFuture;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DisconnectClientCommandQueueFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Slf4j
@Service
@RequiredArgsConstructor
public class DisconnectClientCommandServiceImpl implements DisconnectClientCommandService {
    private final ConcurrentMap<String, SessionDisconnectFuture> awaitingClientDisconnectMap = new ConcurrentHashMap<>();
    private final DisconnectClientCommandQueueFactory disconnectClientCommandQueueFactory;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> clientDisconnectCommandProducer;

    @PostConstruct
    public void init() {
        this.clientDisconnectCommandProducer = disconnectClientCommandQueueFactory.createProducer();
    }

    @Override
    public SettableFuture<Void> startWaitingForDisconnect(UUID disconnectRequesterSessionId, UUID sessionId, String clientId) {
        SessionDisconnectFuture currentlyAwaitingSession = awaitingClientDisconnectMap.get(clientId);
        if (currentlyAwaitingSession != null) {
            currentlyAwaitingSession.getFuture().setException(new MqttException("Another session is trying to connect with this clientId."));
        }

        SettableFuture<Void> awaitClientDisconnectFuture = SettableFuture.create();
        SessionDisconnectFuture sessionDisconnectFuture = new SessionDisconnectFuture(disconnectRequesterSessionId, sessionId, awaitClientDisconnectFuture);
        awaitingClientDisconnectMap.put(clientId, sessionDisconnectFuture);

        return awaitClientDisconnectFuture;
    }

    @Override
    public void clearWaitingFuture(UUID disconnectRequesterSessionId, UUID sessionId, String clientId) {
        SessionDisconnectFuture sessionFuture = new SessionDisconnectFuture(disconnectRequesterSessionId, sessionId, null);
        boolean successfullyRemovedFuture = awaitingClientDisconnectMap.remove(clientId, sessionFuture);
        if (!successfullyRemovedFuture) {
            log.debug("[{}][{}] Failed to clear future, disconnectRequesterSessionId - {}.", clientId, sessionId, disconnectRequesterSessionId);
        }
    }

    @Override
    public void notifyWaitingSession(String clientId, UUID sessionId) {
        SessionDisconnectFuture sessionDisconnectFuture = awaitingClientDisconnectMap.get(clientId);
        if (sessionDisconnectFuture == null) {
            return;
        }

        if (sessionDisconnectFuture.getSessionId().equals(sessionId)) {
            log.trace("[{}][{}] Notifying waiting sessions about disconnect.", clientId, sessionId);
            awaitingClientDisconnectMap.remove(clientId);
            sessionDisconnectFuture.getFuture().set(null);
        } else if (sessionDisconnectFuture.getDisconnectRequesterSessionId().equals(sessionId)) {
            log.trace("[{}][{}] Received DISCONNECT event from session that was awaiting connection.", clientId, sessionId);
            awaitingClientDisconnectMap.remove(clientId);
            sessionDisconnectFuture.getFuture().setException(new MqttException("Session has disconnected already."));
        } else {
            log.debug("[{}][{}] Got disconnect event not from the expected session. Expected sessionId - {}.",
                    clientId, sessionId, sessionDisconnectFuture.getSessionId());
        }
    }

    @Override
    public void disconnectSession(String clientId, UUID sessionId) {
        QueueProtos.DisconnectClientCommandProto disconnectCommand = ProtoConverter.createDisconnectClientCommandProto(sessionId);
        clientDisconnectCommandProducer.send(new TbProtoQueueMsg<>(clientId, disconnectCommand), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}] Disconnect command for session {} sent successfully.", clientId, sessionId);
            }

            @Override
            public void onFailure(Throwable t) {
                log.debug("[{}] Failed to send command for session {}. Reason - {}.", clientId, sessionId, t.getMessage());
                log.trace("Detailed error: ", t);
            }
        });
    }


    @PreDestroy
    public void destroy() {
        if (clientDisconnectCommandProducer != null) {
            clientDisconnectCommandProducer.stop();
        }
    }



    @AllArgsConstructor
    @Getter
    private static class SessionDisconnectFuture {
        private final UUID disconnectRequesterSessionId;
        private final UUID sessionId;
        private final SettableFuture<Void> future;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SessionDisconnectFuture that = (SessionDisconnectFuture) o;
            return disconnectRequesterSessionId.equals(that.disconnectRequesterSessionId) &&
                    sessionId.equals(that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(disconnectRequesterSessionId, sessionId);
        }
    }
}
