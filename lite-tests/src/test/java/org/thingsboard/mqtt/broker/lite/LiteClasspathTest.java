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
package org.thingsboard.mqtt.broker.lite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;
import org.thingsboard.mqtt.broker.dao.service.DeviceMsgServiceContract;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs on a Lite classpath: the application and dao-rocksdb, without queue-kafka and cache-redis.
 */
class LiteClasspathTest {

    @Test
    void theClasspathResolvesToTheLiteEdition() {
        assertThat(TbmqEditionResolver.resolve(getClass().getClassLoader())).isEqualTo(TbmqEdition.LITE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "org.apache.kafka.clients.producer.KafkaProducer",
            "redis.clients.jedis.Jedis",
            "io.lettuce.core.RedisClient",
            "org.springframework.data.redis.core.RedisTemplate",
            "org.thingsboard.mqtt.broker.queue.kafka.TbKafkaAdmin",
            "org.thingsboard.mqtt.broker.cache.TBRedisCacheConfiguration"})
    void noClusterInfrastructureClassCanBeLoaded(String className) {
        assertThatThrownBy(() -> Class.forName(className)).isInstanceOf(ClassNotFoundException.class);
    }

    /**
     * lite-tests takes only the contract and the test-database setup from dao's tests; dao's test configuration
     * would otherwise configure the Lite tests from another module.
     */
    @ParameterizedTest
    @ValueSource(strings = {"logback.xml", "application-test.properties", "sql-test.properties", "xss-policy.xml"})
    void noTestConfigurationComesFromDaosTests(String resource) throws IOException {
        String daoTests = DeviceMsgServiceContract.class.getProtectionDomain().getCodeSource().getLocation().toString();

        assertThat(Collections.list(getClass().getClassLoader().getResources(resource)))
                .map(URL::toString)
                .as("%s on the classpath", resource)
                .noneMatch(url -> url.startsWith(daoTests) || url.startsWith("jar:" + daoTests + "!/"));
    }

}
