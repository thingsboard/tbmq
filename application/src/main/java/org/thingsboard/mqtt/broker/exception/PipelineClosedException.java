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
 * A publish reached the publish pipeline after it stopped accepting, because the broker is shutting down. The publish
 * gets no PUBACK or PUBREC, and its connection closes when the server shuts down, so a client that keeps its session
 * sends the publish again after it reconnects.
 * <p>
 * It is the expected outcome while the broker shuts down, so it carries no stack trace: every publish still on its way
 * to the pipeline fails with it.
 */
public class PipelineClosedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -6219718342958201527L;

    public PipelineClosedException(String message) {
        super(message, null, false, false);
    }
}
