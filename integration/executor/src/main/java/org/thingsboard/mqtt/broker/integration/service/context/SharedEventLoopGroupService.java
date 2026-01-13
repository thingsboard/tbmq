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
package org.thingsboard.mqtt.broker.integration.service.context;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Getter
@Component
public class SharedEventLoopGroupService {

    private EventLoopGroup sharedEventLoopGroup;

    @Value("${integrations.netty.threads-count:0}")
    private int threadsCount;

    @PostConstruct
    public void init() {
        this.sharedEventLoopGroup = new NioEventLoopGroup(threadsCount);
    }

    @PreDestroy
    public void destroy() {
        if (this.sharedEventLoopGroup != null) {
            this.sharedEventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

}
