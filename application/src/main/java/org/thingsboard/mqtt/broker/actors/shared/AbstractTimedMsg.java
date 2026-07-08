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
package org.thingsboard.mqtt.broker.actors.shared;

/**
 * Base for {@link TimedMsg} actor messages: captures the creation timestamp so the actor system can
 * measure how long the message waited in the mailbox before processing (see {@code ContextAwareActor#process},
 * which feeds the {@code *.msgInQueueTime} timer).
 *
 * <p><b>Contract: construct a fresh instance for every send.</b> The timestamp is captured once, in the
 * field initializer, at construction time. A shared/reused instance — e.g. a {@code static} singleton —
 * stamps its creation time at class-load and would then report {@code now - classLoadTime} as its
 * queue-wait time on every send, skewing the {@code *.msgInQueueTime} metric toward JVM uptime instead
 * of real mailbox latency. Never expose or reuse a singleton instance of a {@code TimedMsg}.
 */
public abstract class AbstractTimedMsg implements TimedMsg {

    private final long createdTimeNanos = System.nanoTime();

    @Override
    public long getMsgCreatedTimeNanos() {
        return createdTimeNanos;
    }
}
