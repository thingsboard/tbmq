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
package org.thingsboard.mqtt;

import io.netty.handler.codec.mqtt.MqttPublishMessage;

import java.util.concurrent.Future;

/**
 * Invoked for each inbound PUBLISH routed to this handler - the first registered filter that matches it, otherwise the
 * client's default handler - on the client's handler executor, one message at a time per client and in the order the
 * messages arrived, whatever the executor's pool size. The call returns before the next one starts; the returned
 * future may complete later, and the message's ack waits for it.
 * <p>
 * The whole message is passed rather than topic and payload alone, so a consumer can forward the QoS the message
 * actually arrived at, its retain and dup flags, its packet id, and - under MQTT 5 - its properties.
 * <p>
 * The payload buffer is released by the client once the returned future completes, whatever the handler did. A
 * handler that needs the payload beyond that must copy it, or {@code retain()} the message and release that extra
 * reference itself. The fixed and variable headers are not reference-counted and stay valid.
 * <p>
 * The return type is {@link Future} rather than a Guava {@code ListenableFuture} so an implementation may return a
 * {@link java.util.concurrent.CompletableFuture} instead.
 */
public interface MqttHandler {

    Future<Void> onMessage(MqttPublishMessage msg);
}
