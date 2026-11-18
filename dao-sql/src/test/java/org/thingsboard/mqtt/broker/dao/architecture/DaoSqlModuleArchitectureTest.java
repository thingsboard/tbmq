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
import org.thingsboard.mqtt.broker.dao.JpaDaoConfig;

import java.net.URISyntaxException;
import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the SQL module apart from the RocksDB module: the two embedded and relational stores are alternatives,
 * never both on one classpath.
 * ArchUnit fails a rule that matches no classes, so an empty import cannot pass silently.
 */
class DaoSqlModuleArchitectureTest {

    private static final ArchRule NO_ROCKSDB_RULE = noClasses().should().dependOnClassesThat(
                    resideInAnyPackage("org.rocksdb.."))
            .as("dao-sql classes must not depend on RocksDB");

    @Test
    void daoSqlDependsOnNoRocksDb() {
        NO_ROCKSDB_RULE.check(daoSqlClasses());
    }

    @Test
    void daoSqlImportIsNotEmpty() {
        assertThat(daoSqlClasses().contain(JpaDaoConfig.class)).isTrue();
    }

    private static JavaClasses daoSqlClasses() {
        try {
            Location location = Location.of(JpaDaoConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importLocations(Set.of(location));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
