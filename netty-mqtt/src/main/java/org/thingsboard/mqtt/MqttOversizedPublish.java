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

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Takes the place, in the pipeline, of a PUBLISH over the client's {@code maxBytesInMessage} that
 * {@link MqttOversizedPublishGuard} skipped: what the client needs to acknowledge and report it. {@code packetId} is -1
 * for QoS 0, and {@code remainingLength} is the packet's size past its fixed header.
 */
record MqttOversizedPublish(String topic, MqttQoS qos, int packetId, int remainingLength) {
}
