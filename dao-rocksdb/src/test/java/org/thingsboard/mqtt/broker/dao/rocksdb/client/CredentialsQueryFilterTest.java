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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialsQueryFilterTest {

    private static ClientCredentialsQuery query(List<ClientType> clientTypes, List<ClientCredentialsType> credentialsTypes,
                                                String username, String clientId, String certificateCn) {
        return new ClientCredentialsQuery(new PageLink(10), clientTypes, credentialsTypes, username, clientId, certificateCn);
    }

    @Test
    void aQueryThatRestrictsNothingHasNoFilter() {
        assertThat(CredentialsQueryFilter.of(query(null, null, null, null, null))).isNull();
        assertThat(CredentialsQueryFilter.of(query(List.of(), List.of(), "", "", ""))).isNull();
    }

    @Test
    void everyRestrictionGivesAFilter() {
        assertThat(CredentialsQueryFilter.of(query(List.of(ClientType.DEVICE), null, null, null, null))).isNotNull();
        assertThat(CredentialsQueryFilter.of(query(null, List.of(ClientCredentialsType.SCRAM), null, null, null))).isNotNull();
        assertThat(CredentialsQueryFilter.of(query(null, null, "user", null, null))).isNotNull();
        assertThat(CredentialsQueryFilter.of(query(null, null, null, "client", null))).isNotNull();
        assertThat(CredentialsQueryFilter.of(query(null, null, null, null, "cn"))).isNotNull();
    }

}
