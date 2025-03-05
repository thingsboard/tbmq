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
package org.thingsboard.mqtt.broker.actors.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thingsboard.mqtt.broker.actors.ActorStatsManager;
import org.thingsboard.mqtt.broker.actors.DefaultTbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbActorSystemSettings;

@Configuration
public class ActorsConfiguration {

    @Value("${actors.system.throughput:5}")
    private int actorThroughput;
    @Value("${actors.system.max-actor-init-attempts:10}")
    private int maxActorInitAttempts;
    @Value("${actors.system.scheduler-pool-size:1}")
    private int schedulerPoolSize;

    @Bean
    public TbActorSystemSettings actorSystemSettings() {
        return new TbActorSystemSettings(actorThroughput, schedulerPoolSize, maxActorInitAttempts);
    }

    @Bean(destroyMethod = "destroy")
    public TbActorSystem actorSystem(TbActorSystemSettings actorSystemSettings, ActorStatsManager actorStatsManager) {
        return new DefaultTbActorSystem(actorSystemSettings, actorStatsManager);
    }
}
