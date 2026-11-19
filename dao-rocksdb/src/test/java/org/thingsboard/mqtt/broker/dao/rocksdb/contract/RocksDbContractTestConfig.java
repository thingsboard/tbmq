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

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.FileSystemUtils;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Spring context the contract suite runs on over the RocksDB store: an empty store in a fresh directory that is
 * deleted when the context closes, plus what the services under test need from the rest of the application. The
 * subclasses add the services under test to it.
 * <p>
 * The scan registers the store, the entity store and the other beans of the module. It leaves out the properties and the
 * memory budget, which this class defines with the temporary directory and a fixed budget, and the device message
 * queue, which needs the broker's device settings and has no contract case.
 */
@Configuration
@ComponentScan(basePackages = "org.thingsboard.mqtt.broker.dao.rocksdb",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {StoreProperties.class, StoreMemoryBudget.class}),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "org\\.thingsboard\\.mqtt\\.broker\\.dao\\.rocksdb\\.devicemsg\\..*"),
                // the nested configurations of the subclasses belong to their own context only
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "org\\.thingsboard\\.mqtt\\.broker\\.dao\\.rocksdb\\.contract\\..*")
        })
public class RocksDbContractTestConfig {

    private static final long MEMORY_BUDGET_BYTES = 1L << 30;

    /**
     * Created before the properties, so Spring destroys it after the store has closed.
     */
    @Bean(destroyMethod = "close")
    public StoreDirectory storeDirectory() throws IOException {
        return new StoreDirectory(Files.createTempDirectory("tbmq-contract-store"));
    }

    @Bean
    public StoreProperties storeProperties(StoreDirectory storeDirectory) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(storeDirectory.path().toString());
        return properties;
    }

    @Bean
    public StoreMemoryBudget storeMemoryBudget() {
        return StoreMemoryBudget.forContainerMemory(MEMORY_BUDGET_BYTES, 0, 0);
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE,
                CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE,
                CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public record StoreDirectory(Path path) implements AutoCloseable {

        @Override
        public void close() {
            try {
                FileSystemUtils.deleteRecursively(path);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete the contract store directory " + path, e);
            }
        }

    }

}
