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
package org.thingsboard.mqtt.broker.session;


import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.Tenant;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import javax.annotation.Nullable;
import java.util.UUID;

public class SessionInfoCreator {

    public static QueueProtos.SessionInfoProto createProto(SessionInfo sessionInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        QueueProtos.SessionInfoProto.Builder builder = QueueProtos.SessionInfoProto.newBuilder();
        builder
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setPersistent(sessionInfo.isPersistent())
                .setClientId(clientInfo.getClientId());
        Tenant tenant = clientInfo.getTenant();
        if (tenant != null) {
            builder
                    .setTenantIdMSB(tenant.getTenantId().getMostSignificantBits())
                    .setTenantIdLSB(tenant.getTenantId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static SessionInfo create(UUID sessionId, String clientId, boolean persistent, @Nullable UUID tenantId) {
        ClientInfo.ClientInfoBuilder clientInfoBuilder = ClientInfo.builder();
        clientInfoBuilder
                .clientId(clientId);
        if (tenantId != null) {
            clientInfoBuilder
                    .tenant(Tenant.builder()
                            .tenantId(tenantId)
                            .build());
        }
        return SessionInfo.builder()
                .sessionId(sessionId)
                .persistent(persistent)
                .clientInfo(clientInfoBuilder.build())
                .build();
    }

}
