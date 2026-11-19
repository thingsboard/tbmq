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

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsServiceImpl;
import org.thingsboard.mqtt.broker.dao.service.DefaultTopicValidationService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsServiceImpl;
import org.thingsboard.mqtt.broker.dao.user.UserServiceImpl;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionServiceImpl;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionServiceImpl;

/**
 * The real services a WebSocket connection touches, for the contract classes over the RocksDB store: the connection and
 * subscription services with the user, credentials, settings and topic validation services they inject. The topic
 * validation reads {@code mqtt.topic.max-segments-count}, which the class using this configuration sets (see
 * {@link #TOPIC_PROPERTIES}).
 */
@Configuration
@Import({WebSocketConnectionServiceImpl.class, WebSocketSubscriptionServiceImpl.class, UserServiceImpl.class,
        MqttClientCredentialsServiceImpl.class, AdminSettingsServiceImpl.class, DefaultTopicValidationService.class,
        TbCacheOps.class, CacheNameResolver.class, CacheProperties.class})
class RocksDbWebSocketServices {

    /**
     * The property the topic validation needs, for {@code @TestPropertySource(properties = ...)}: no limit on the
     * topic segments, as {@code application-test.properties} of the SQL suite.
     */
    static final String TOPIC_PROPERTIES = "mqtt.topic.max-segments-count=0";

}
