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
package org.thingsboard.mqtt.broker.dao.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.dao.Dao;
import org.thingsboard.mqtt.broker.dao.user.UserServiceImpl;

import java.net.URISyntaxException;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the dao module free of persistence and cluster infrastructure: the JPA and JDBC code lives in dao-sql,
 * so the services and the {@code *Dao} interfaces here must run on any store.
 * ArchUnit fails a rule that matches no classes, so an empty import cannot pass silently.
 */
class DaoArchitectureTest {

    private static final ArchRule NO_INFRASTRUCTURE_RULE = noClasses().should().dependOnClassesThat(
                    resideInAnyPackage(
                            "jakarta.persistence..", "org.hibernate..", "org.springframework.data..",
                            "org.springframework.jdbc..", "javax.sql..", "com.zaxxer.hikari..", "org.postgresql..",
                            "org.rocksdb..", "org.apache.kafka..", "redis.clients..", "io.lettuce..")
                            .and(not(resideInAnyPackage("org.hibernate.validator.."))))
            .as("dao classes must not depend on persistence or cluster infrastructure packages");

    @Test
    void daoDependsOnNoPersistenceOrClusterInfrastructure() {
        NO_INFRASTRUCTURE_RULE.check(daoClasses());
    }

    @Test
    void daoImportIsNotEmpty() {
        assertThat(daoClasses().contain(UserServiceImpl.class)).isTrue();
    }

    private static JavaClasses daoClasses() {
        try {
            Location location = Location.of(Dao.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importLocations(Set.of(location));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
