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
package org.thingsboard.mqtt.broker.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;

@Data
@EqualsAndHashCode
@Entity
@Table(name = ModelConstants.UNAUTHORIZED_CLIENT_COLUMN_FAMILY_NAME)
public class UnauthorizedClientEntity implements ToData<UnauthorizedClient> {

    @Id
    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_CLIENT_ID_PROPERTY)
    private String clientId;

    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_IP_ADDRESS_PROPERTY)
    private String ipAddress;

    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_TS_PROPERTY)
    protected Long ts;

    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_USERNAME_PROPERTY)
    private String username;

    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_PASSWORD_PROVIDED_PROPERTY)
    private boolean passwordProvided;

    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_TLS_USED_PROPERTY)
    private boolean tlsUsed;

    @Column(name = ModelConstants.UNAUTHORIZED_CLIENT_REASON_PROPERTY)
    private String reason;

    public UnauthorizedClientEntity() {
    }

    public UnauthorizedClientEntity(UnauthorizedClient unauthorizedClient) {
        this.clientId = unauthorizedClient.getClientId();
        this.ipAddress = unauthorizedClient.getIpAddress();
        this.ts = unauthorizedClient.getTs();
        this.username = unauthorizedClient.getUsername();
        this.passwordProvided = unauthorizedClient.isPasswordProvided();
        this.tlsUsed = unauthorizedClient.isTlsUsed();
        this.reason = unauthorizedClient.getReason();
    }

    @Override
    public UnauthorizedClient toData() {
        return UnauthorizedClient.builder()
                .clientId(clientId)
                .ipAddress(ipAddress)
                .ts(ts)
                .username(username)
                .passwordProvided(passwordProvided)
                .tlsUsed(tlsUsed)
                .reason(reason)
                .build();
    }
}
