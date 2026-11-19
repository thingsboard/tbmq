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
package org.thingsboard.mqtt.broker.dao.rocksdb.contract;

import org.junit.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsServiceImpl;
import org.thingsboard.mqtt.broker.dao.contract.AbstractMqttClientCredentialsContractTest;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = {RocksDbContractTestConfig.class, RocksDbMqttClientCredentialsContractTest.Services.class},
        loader = AnnotationConfigContextLoader.class)
public class RocksDbMqttClientCredentialsContractTest extends AbstractMqttClientCredentialsContractTest {

    @Configuration
    @Import({MqttClientCredentialsServiceImpl.class, TbCacheOps.class, CacheNameResolver.class, CacheProperties.class})
    static class Services {
    }

    // The two cases below differ from PostgreSQL by design: the SQL filter is a LIKE over the credentials value JSON
    // text, which also matches the value of another field, and where a '%' or '_' of the filter is a wildcard. The
    // store matches the parsed field of the value, as plain text.

    @Test
    public void aUsernameFilterDoesNotMatchAnotherFieldsValue() throws Exception {
        saveBasic("by client id", "dev", "a");

        assertThat(countByUsername("dev")).isZero();
        assertThat(countByUsername("a")).isEqualTo(1);
        assertThat(countByClientId("dev")).isEqualTo(1);
        assertThat(countByClientId("a")).isZero();
    }

    @Test
    public void wildcardAndQuoteCharactersInFiltersMatchLiterally() throws Exception {
        saveBasic("underscore", "c-1", "a_b");
        saveBasic("x", "c-2", "axb");
        saveBasic("percent", "c-3", "50%off");
        saveBasic("quote", "c-4", "say \"hi\"");

        assertThat(countByUsername("a_b")).isEqualTo(1);
        assertThat(countByUsername("a%b")).isZero();
        assertThat(countByUsername("%")).isEqualTo(1);
        assertThat(countByUsername("_")).isEqualTo(1);
        assertThat(countByUsername("\"hi\"")).isEqualTo(1);
        assertThat(countByUsername("\"")).isEqualTo(1);
        assertThat(countByUsername("\\")).isZero();
    }

    private long countByUsername(String username) {
        return count(new ClientCredentialsQuery(new PageLink(100), null, null, username, null, null));
    }

    private long countByClientId(String clientId) {
        return count(new ClientCredentialsQuery(new PageLink(100), null, null, null, clientId, null));
    }

    private long count(ClientCredentialsQuery query) {
        return mqttClientCredentialsService.getCredentialsV2(query).getTotalElements();
    }

}
