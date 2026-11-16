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
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;
import org.thingsboard.mqtt.broker.dao.service.DeviceMsgServiceContract;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs on a Lite classpath: the application and dao-rocksdb, without queue-kafka and cache-redis.
 */
class LiteClasspathTest {

    private static final String RESTRICTED_DAO_TEST_JAR_SUFFIX = "-lite-tests.jar";
    private static final Pattern DAO_TEST_JAR = Pattern.compile("/dao-\\d[^/]*-tests\\.jar!/");
    // in the reactor the application's jars carry its final name, thingsboard-mqtt-broker; in a repository, its artifact id
    private static final Pattern APPLICATION_TEST_JAR = Pattern.compile("/(application|thingsboard-mqtt-broker)-\\d[^/]*-tests\\.jar!/");

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

    @Test
    void daosTestClassesComeFromTheRestrictedJar() {
        assertThat(DeviceMsgServiceContract.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .endsWith(RESTRICTED_DAO_TEST_JAR_SUFFIX);
    }

    /**
     * lite-tests takes only the contract and the test-database setup from dao's tests; dao's test configuration
     * would otherwise configure the Lite tests from another module. Checked over the whole classpath, so it also
     * catches dao's full test-jar or its test-classes directory.
     */
    @ParameterizedTest
    @ValueSource(strings = {"logback.xml", "application-test.properties", "xss-policy.xml"})
    void noTestConfigurationComesFromDaosTests(String resource) throws IOException {
        assertThat(Collections.list(getClass().getClassLoader().getResources(resource)))
                .map(URL::toString)
                .as("%s on the classpath", resource)
                .noneMatch(LiteClasspathTest::isDaoTestOutput);
    }

    /**
     * The test database of {@code @DaoSqlTest} is dao's own, taken from the restricted jar rather than kept as a copy.
     */
    @Test
    void theTestDatabaseSettingsComeFromTheRestrictedJar() throws IOException {
        assertThat(Collections.list(getClass().getClassLoader().getResources("sql-test.properties")))
                .map(URL::toString)
                .singleElement()
                .satisfies(url -> assertThat(url).endsWith(RESTRICTED_DAO_TEST_JAR_SUFFIX + "!/sql-test.properties"));
    }

    // dao's test-classes directory, or any of its test jars (dao-<version>-tests.jar, dao-<version>-lite-tests.jar)
    private static boolean isDaoTestOutput(String url) {
        return url.contains("/dao/target/test-classes/") || DAO_TEST_JAR.matcher(url).find();
    }

    /**
     * The application's test-jar leaves out the cluster test infrastructure; its test-classes directory would not.
     */
    @Test
    void theApplicationsTestClassesComeFromItsTestJar() {
        assertThat(AbstractPubSubIntegrationTest.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .endsWith("-tests.jar");
    }

    /**
     * The Lite suites run the application's tests with the test configuration of lite-tests.
     */
    @ParameterizedTest
    @ValueSource(strings = {"logback.xml", "application-test.properties"})
    void noTestConfigurationComesFromTheApplicationsTests(String resource) throws IOException {
        assertThat(Collections.list(getClass().getClassLoader().getResources(resource)))
                .map(URL::toString)
                .as("%s on the classpath", resource)
                .noneMatch(LiteClasspathTest::isApplicationTestOutput);
    }

    // the application's test-classes directory, or its test-jar
    private static boolean isApplicationTestOutput(String url) {
        return url.contains("/application/target/test-classes/") || APPLICATION_TEST_JAR.matcher(url).find();
    }

}
