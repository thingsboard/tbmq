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
package org.thingsboard.mqtt.broker.server;

import io.netty.channel.Channel;

public interface MqttServerBootstrap {

    String getHost();

    int getPort();

    int getBossGroupThreadCount();

    int getWorkerGroupThreadCount();

    boolean isKeepAlive();

    int getShutdownQuietPeriod();

    int getShutdownTimeout();

    AbstractMqttChannelInitializer getChannelInitializer();

    String getServerName();

    Channel getServerChannel();

}
