/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao;

import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AbstractRedisContainer {

    @ClassRule(order = 0)
    public static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:8.0")
            .withExposedPorts(6379)
            .withLogConsumer(x -> log.warn("{}", x.getUtf8StringWithoutLineEnding()))
            .withCommand("valkey-server", "--requirepass", "password")
            .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofSeconds(10));

    @ClassRule(order = 1)
    public static ExternalResource resource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            valkey.start();

            Thread.sleep(TimeUnit.SECONDS.toMillis(5));

            System.setProperty("redis.connection.type", "standalone");
            System.setProperty("redis.standalone.host", valkey.getHost());
            System.setProperty("redis.standalone.port", String.valueOf(valkey.getMappedPort(6379)));
            System.setProperty("redis.password", "password");
        }

        @Override
        protected void after() {
            valkey.stop();
            List.of("redis.connection.type", "redis.standalone.host", "redis.standalone.port", "redis.password")
                    .forEach(System.getProperties()::remove);
        }
    };

}
