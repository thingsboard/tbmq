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
package org.thingsboard.mqtt.broker.dao.rocksdb.client;

import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore.ProjectedEntity;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The filter of {@link RocksDbMqttClientCredentialsDao#findAllV2}: the same rules as the SQL query of
 * {@code MqttClientCredentialsRepository.findAllV2}, which gates the credentials type of each field filter. Only the
 * matching differs: the SQL query runs a {@code LIKE} over the credentials value JSON text, this filter looks for the
 * text in the parsed field, ignoring case, and the characters of the filter have no special meaning. The text search
 * on the name is the page link's, not this filter's.
 * <ul>
 *     <li>client types and credentials types: exact match; none given, or an empty list, is no restriction;</li>
 *     <li>no username, client id or certificate CN: no further restriction;</li>
 *     <li>username alone: MQTT_BASIC or SCRAM credentials whose user name contains it;</li>
 *     <li>client id alone: MQTT_BASIC credentials whose client id contains it;</li>
 *     <li>certificate CN alone: X_509 credentials whose certificate CN pattern contains it;</li>
 *     <li>username and client id: MQTT_BASIC credentials that match both;</li>
 *     <li>certificate CN together with a username or a client id: nothing.</li>
 * </ul>
 * An empty string is as good as none.
 */
final class CredentialsQueryFilter implements Predicate<ProjectedEntity> {

    private static final String CLIENT_TYPE = "clientType";
    private static final String CREDENTIALS_TYPE = "credentialsType";
    private static final String USER_NAME = "userName";
    private static final String CLIENT_ID = "clientId";
    private static final String CERT_CN_PATTERN = "certCnPattern";

    private final List<ClientType> clientTypes;
    private final List<ClientCredentialsType> credentialsTypes;
    private final String username;
    private final String clientId;
    private final String certificateCn;

    /**
     * The filter of {@code query}, or {@code null} when it restricts nothing (no client types, no credentials types, no
     * username, client id or certificate CN), so that the page is not tested row by row for nothing.
     */
    static CredentialsQueryFilter of(ClientCredentialsQuery query) {
        CredentialsQueryFilter filter = new CredentialsQueryFilter(query);
        return filter.restrictsNothing() ? null : filter;
    }

    CredentialsQueryFilter(ClientCredentialsQuery query) {
        this.clientTypes = query.getClientTypeList();
        this.credentialsTypes = query.getCredentialsTypeList();
        this.username = lowerCaseOrNull(query.getUsername());
        this.clientId = lowerCaseOrNull(query.getClientId());
        this.certificateCn = lowerCaseOrNull(query.getCertificateCn());
    }

    private boolean restrictsNothing() {
        return (clientTypes == null || clientTypes.isEmpty()) && (credentialsTypes == null || credentialsTypes.isEmpty())
                && username == null && clientId == null && certificateCn == null;
    }

    @Override
    public boolean test(ProjectedEntity entity) {
        if (clientTypes != null && !clientTypes.isEmpty() && !clientTypes.contains(entity.filterValues().get(CLIENT_TYPE))) {
            return false;
        }
        Object credentialsType = entity.filterValues().get(CREDENTIALS_TYPE);
        if (credentialsTypes != null && !credentialsTypes.isEmpty() && !credentialsTypes.contains(credentialsType)) {
            return false;
        }
        return matchesCredentialFields(entity, credentialsType);
    }

    private boolean matchesCredentialFields(ProjectedEntity entity, Object credentialsType) {
        boolean hasUsername = username != null;
        boolean hasClientId = clientId != null;
        boolean hasCertificateCn = certificateCn != null;
        if (!hasUsername && !hasClientId && !hasCertificateCn) {
            return true;
        }
        if (hasCertificateCn && (hasUsername || hasClientId)) {
            return false;
        }
        if (hasCertificateCn) {
            return credentialsType == ClientCredentialsType.X_509 && contains(entity, CERT_CN_PATTERN, certificateCn);
        }
        if (hasUsername && hasClientId) {
            return credentialsType == ClientCredentialsType.MQTT_BASIC
                    && contains(entity, USER_NAME, username) && contains(entity, CLIENT_ID, clientId);
        }
        if (hasClientId) {
            return credentialsType == ClientCredentialsType.MQTT_BASIC && contains(entity, CLIENT_ID, clientId);
        }
        return (credentialsType == ClientCredentialsType.MQTT_BASIC || credentialsType == ClientCredentialsType.SCRAM)
                && contains(entity, USER_NAME, username);
    }

    private static boolean contains(ProjectedEntity entity, String field, String lowerCaseSearch) {
        return entity.filterValues().get(field) instanceof String value
                && value.toLowerCase(Locale.ROOT).contains(lowerCaseSearch);
    }

    private static String lowerCaseOrNull(String value) {
        return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

}
