/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.cluster;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;

@Component
@ConditionalOnProperty(prefix = "zk", value = "enabled", havingValue = "true", matchIfMissing = false)
@ConfigurationProperties(prefix = "zk")
@Data
public class ZkConfiguration {
    private String url;
    private Integer retryIntervalMs;
    private Integer connectionTimeoutMs;
    private Integer sessionTimeoutMs;
    private Integer reconnectScheduledDelayMs;
    private String zkDir;

    @PostConstruct
    public void validate() {
        Assert.hasLength(url, missingProperty("zk.url"));
        Assert.notNull(retryIntervalMs, missingProperty("zk.retry_interval_ms"));
        Assert.notNull(connectionTimeoutMs, missingProperty("zk.connection_timeout_ms"));
        Assert.notNull(sessionTimeoutMs, missingProperty("zk.session_timeout_ms"));
    }

    private static String missingProperty(String propertyName) {
        return "The " + propertyName + " property need to be set!";
    }
}
