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
package org.thingsboard.mqtt.broker.exception;

import java.io.Serial;

/**
 * A publish failed because a limit was hit, not because of an error: the client gets Quota exceeded (0x97) on MQTT 5.
 * <p>
 * It is the expected outcome on the overload path, so it carries no stack trace: filling one in for every refused
 * publish would cost the most when the broker can least afford it.
 */
public class PublishLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 3954184236714382690L;

    public PublishLimitExceededException(String message) {
        super(message, null, false, false);
    }
}
