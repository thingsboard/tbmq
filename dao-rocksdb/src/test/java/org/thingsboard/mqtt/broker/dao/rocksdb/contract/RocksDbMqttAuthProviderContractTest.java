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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderServiceImpl;
import org.thingsboard.mqtt.broker.dao.contract.AbstractMqttAuthProviderContractTest;

@ContextConfiguration(classes = {RocksDbContractTestConfig.class, RocksDbMqttAuthProviderContractTest.Services.class},
        loader = AnnotationConfigContextLoader.class)
public class RocksDbMqttAuthProviderContractTest extends AbstractMqttAuthProviderContractTest {

    @Configuration
    @Import(MqttAuthProviderServiceImpl.class)
    static class Services {

        /**
         * The disabled default provider of every type, as the SQL system data has them.
         */
        @Bean
        InitializingBean defaultAuthProviders(MqttAuthProviderDao dao) {
            return () -> {
                for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
                    dao.save(MqttAuthProvider.defaultAuthProvider(type, false));
                }
            };
        }

    }

}
