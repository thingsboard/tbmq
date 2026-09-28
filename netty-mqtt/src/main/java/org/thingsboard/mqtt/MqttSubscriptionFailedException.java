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

/**
 * Fails the future {@link MqttClient#on} returns when the server refuses the topic filter: its SUBACK carries the
 * failure code {@code 0x80} for it. Nothing is registered for the filter then.
 */
public class MqttSubscriptionFailedException extends RuntimeException {

    public MqttSubscriptionFailedException(String message) {
        super(message);
    }

}
