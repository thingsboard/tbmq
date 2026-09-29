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
 * Fails a publish, subscribe or unsubscribe made while all 65 535 message IDs are held by ones still in flight: MQTT
 * forbids reusing the ID of one not yet acknowledged, so the new one is not sent. It may succeed once one completes.
 */
public class MessageIdsExhaustedException extends RuntimeException {

    public MessageIdsExhaustedException(String message) {
        super(message);
    }

}
