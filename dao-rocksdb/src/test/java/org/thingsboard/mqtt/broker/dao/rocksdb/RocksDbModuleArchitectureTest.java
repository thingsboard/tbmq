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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps dao-rocksdb free of the cluster infrastructure (Kafka, Redis) and of JPA. The module is imported by the
 * location that holds RocksDbStore, so the rule sees exactly its main classes.
 */
class RocksDbModuleArchitectureTest {

    private static final ArchRule NO_KAFKA_REDIS_OR_JPA_RULE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.apache.kafka..",
                    "redis.clients..", "io.lettuce..", "org.springframework.data.redis..", "io.github.bucket4j.redis..",
                    "jakarta.persistence..", "org.hibernate..", "com.zaxxer.hikari..")
            .as("dao-rocksdb classes must not depend on Kafka, Redis or JPA");

    @Test
    void daoRocksDbDependsOnNoKafkaRedisOrJpa() {
        NO_KAFKA_REDIS_OR_JPA_RULE.check(daoRocksDbClasses());
    }

    @Test
    void theImportIsNotEmpty() {
        JavaClasses classes = daoRocksDbClasses();
        assertThat(classes.contain(RocksDbStore.class)).isTrue();
        assertThat(classes).isNotEmpty();
    }

    private static JavaClasses daoRocksDbClasses() {
        try {
            Location location = Location.of(RocksDbStore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importLocations(Set.of(location));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
