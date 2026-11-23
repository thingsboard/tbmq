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
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.thingsboard.mqtt.broker.testsupport.EditionTestInfrastructure;
import org.thingsboard.mqtt.broker.testsupport.lite.LiteEditionTestInfrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LiteEditionTestInfrastructureTest {

    @Test
    void exactlyTheLiteInfrastructureIsOnTheLiteTestClasspath() {
        assertThat(ServiceLoader.load(EditionTestInfrastructure.class).stream().map(ServiceLoader.Provider::type))
                .containsExactly(LiteEditionTestInfrastructure.class);
    }

    @Test
    void theLiteContextPropertiesAreEmpty() {
        assertThat(EditionTestInfrastructure.load().contextProperties()).isEmpty();
    }

    @Test
    void theClassRuleGivesTheStoreAnExistingEmptyDirectoryAndRemovesItAfterwards() throws Throwable {
        String before = System.getProperty(TempStorePathRule.STORE_PATH);
        TestRule rule = EditionTestInfrastructure.loadClassRule();
        AtomicReference<Path> storePath = new AtomicReference<>();
        Statement testClass = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Path dir = Path.of(System.getProperty(TempStorePathRule.STORE_PATH));
                storePath.set(dir);
                assertThat(dir).isDirectory().isEmptyDirectory();
                // what the store leaves behind is removed too
                Files.writeString(dir.resolve("CURRENT"), "MANIFEST-000001");
            }
        };

        rule.apply(testClass, Description.EMPTY).evaluate();

        assertThat(storePath.get()).isNotNull().doesNotExist();
        assertThat(System.getProperty(TempStorePathRule.STORE_PATH)).isEqualTo(before);
    }

}
