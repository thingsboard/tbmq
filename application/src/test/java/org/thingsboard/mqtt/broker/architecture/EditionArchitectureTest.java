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
package org.thingsboard.mqtt.broker.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.architecture.fixture.KafkaImportingFixture;
import org.thingsboard.mqtt.broker.cache.TBRedisCacheConfiguration;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.dao.JpaDaoConfig;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaAdmin;
import org.thingsboard.mqtt.broker.queue.provider.integration.TbmqIntegrationDownlinkQueueProvider;
import org.thingsboard.mqtt.broker.queue.provider.integration.TbmqIntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.queue.provider.integration.TbmqIntegrationUplinkQueueProvider;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicServiceImpl;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Keeps the edition-neutral modules free of infrastructure dependencies (Kafka, Redis, RocksDB, JPA).
 * Each module is imported by the location (directory or jar) that holds its anchor class, so the rules
 * see exactly that module's main classes, never the test fixture or other modules.
 * ArchUnit fails a rule that matches no classes, so an empty import cannot pass silently.
 */
class EditionArchitectureTest {

    private static final String[] KAFKA = {"org.apache.kafka.."};
    private static final String[] REDIS = {
            "redis.clients..", "io.lettuce..", "org.springframework.data.redis..", "io.github.bucket4j.redis.."};
    private static final String[] ROCKSDB = {"org.rocksdb.."};
    private static final String[] JPA = {"jakarta.persistence..", "org.hibernate..", "com.zaxxer.hikari.."};

    private static final ArchRule APPLICATION_RULE = forbid(KAFKA, REDIS, JPA, ROCKSDB);
    private static final ArchRule DAO_RULE = forbid(KAFKA, REDIS, ROCKSDB);
    private static final ArchRule NO_KAFKA_REDIS_ROCKSDB_RULE = forbid(KAFKA, REDIS, ROCKSDB);
    private static final ArchRule QUEUE_KAFKA_RULE = forbid(REDIS, ROCKSDB);
    private static final ArchRule CACHE_REDIS_RULE = forbid(KAFKA, ROCKSDB);

    /**
     * Not Kafka-backed: DefaultTbAppService takes an optional ObjectProvider of the admin, IntegrationManagerServiceImpl
     * takes a lazy provider used only by cluster beans.
     */
    private static final Set<String> KAFKA_BACKED_BEAN_EXCEPTIONS = Set.of(
            "DefaultTbAppService", "IntegrationManagerServiceImpl");

    private static final DescribedPredicate<JavaClass> DEPENDS_ON_KAFKA_BACKED_TYPE = new DescribedPredicate<>(
            "are not a named exception and depend on a TbQueueAdmin, or on a queue factory or provider") {
        @Override
        public boolean test(JavaClass type) {
            return !KAFKA_BACKED_BEAN_EXCEPTIONS.contains(type.getSimpleName())
                    && type.getDirectDependenciesFromSelf().stream()
                    .map(Dependency::getTargetClass)
                    .anyMatch(EditionArchitectureTest::isKafkaBackedType);
        }
    };

    private static final ArchRule KAFKA_BACKED_BEANS_RULE = classes()
            .that(DEPENDS_ON_KAFKA_BACKED_TYPE)
            .should().beAnnotatedWith(ClusterComponent.class)
            .as("classes that depend on a Kafka queue factory, provider or admin must be cluster-only");

    @Test
    void applicationDependsOnNoInfrastructure() {
        APPLICATION_RULE.check(classesOf(ThingsboardMqttBrokerApplication.class));
    }

    @Test
    void daoDependsOnNoKafkaRedisOrRocksDb() {
        DAO_RULE.check(classesOf(JpaDaoConfig.class));
    }

    @Test
    void queueDependsOnNoKafkaRedisOrRocksDb() {
        NO_KAFKA_REDIS_ROCKSDB_RULE.check(classesOf(TbQueueProducer.class));
    }

    @Test
    void cacheDependsOnNoKafkaRedisOrRocksDb() {
        NO_KAFKA_REDIS_ROCKSDB_RULE.check(classesOf(TbCacheOps.class));
    }

    @Test
    void utilDependsOnNoKafkaRedisOrRocksDb() {
        NO_KAFKA_REDIS_ROCKSDB_RULE.check(classesOf(JacksonUtil.class));
    }

    @Test
    void clusterIntegrationApiDependsOnNoKafkaRedisOrRocksDb() {
        NO_KAFKA_REDIS_ROCKSDB_RULE.check(classesOf(IntegrationTopicServiceImpl.class));
    }

    @Test
    void queueKafkaDependsOnNoRedisOrRocksDb() {
        QUEUE_KAFKA_RULE.check(classesOf(TbKafkaAdmin.class));
    }

    @Test
    void cacheRedisDependsOnNoKafkaOrRocksDb() {
        CACHE_REDIS_RULE.check(classesOf(TBRedisCacheConfiguration.class));
    }

    @Test
    void kafkaBackedBeansAreClusterOnly() {
        KAFKA_BACKED_BEANS_RULE.check(classesOf(ThingsboardMqttBrokerApplication.class));
        KAFKA_BACKED_BEANS_RULE.check(classesOf(IntegrationTopicServiceImpl.class));
    }

    @Test
    void theThreeIntegrationQueueProvidersInCommonQueueAreClusterOnly() {
        assertThat(List.of(TbmqIntegrationMsgQueueProvider.class, TbmqIntegrationDownlinkQueueProvider.class,
                TbmqIntegrationUplinkQueueProvider.class))
                .allMatch(c -> c.isAnnotationPresent(ClusterComponent.class));
    }

    @Test
    void everyModuleImportIsNotEmpty() {
        for (Class<?> anchor : new Class<?>[]{ThingsboardMqttBrokerApplication.class, JpaDaoConfig.class,
                TbQueueProducer.class, TbKafkaAdmin.class, TbCacheOps.class, TBRedisCacheConfiguration.class,
                JacksonUtil.class, IntegrationTopicServiceImpl.class}) {
            assertThat(classesOf(anchor).contain(anchor)).as("import of %s", anchor.getSimpleName()).isTrue();
            assertThat(classesOf(anchor)).as("classes imported for %s", anchor.getSimpleName()).isNotEmpty();
        }
    }

    @Test
    void applicationImportExcludesTestFixture() {
        assertThat(classesOf(ThingsboardMqttBrokerApplication.class).contain(KafkaImportingFixture.class)).isFalse();
    }

    @Test
    void ruleCatchesAKafkaImport() {
        assertThatThrownBy(() -> APPLICATION_RULE.check(new ClassFileImporter().importClasses(KafkaImportingFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.apache.kafka");
    }

    private static boolean isKafkaBackedType(JavaClass type) {
        return type.isAssignableTo(TbQueueAdmin.class)
                || type.getPackageName().startsWith("org.thingsboard.mqtt.broker.queue.provider")
                && (type.getSimpleName().endsWith("QueueFactory") || type.getSimpleName().endsWith("QueueProvider"));
    }

    private static ArchRule forbid(String[]... packageRoots) {
        return noClasses().should().dependOnClassesThat()
                .resideInAnyPackage(Stream.of(packageRoots).flatMap(Stream::of).toArray(String[]::new))
                .as("classes must not depend on the forbidden infrastructure packages");
    }

    private static JavaClasses classesOf(Class<?> anchor) {
        try {
            Location location = Location.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importLocations(Set.of(location));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
