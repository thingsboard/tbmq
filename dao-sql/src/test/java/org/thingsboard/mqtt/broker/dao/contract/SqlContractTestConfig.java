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
package org.thingsboard.mqtt.broker.dao.contract;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The Spring context of the contract suite over PostgreSQL: the whole broker scanned, as every dao-sql service test does.
 * Abstract like {@code AbstractServiceTest}, so that no component scan of another context (the application tests scan
 * {@code org.thingsboard.mqtt.broker} with this test-jar on the classpath) instantiates it; the {@code Sql*ContractTest}
 * classes name it in {@code @ContextConfiguration}.
 */
@Configuration
@EnableAutoConfiguration(excludeName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@ComponentScan("org.thingsboard.mqtt.broker")
public abstract class SqlContractTestConfig {
}
