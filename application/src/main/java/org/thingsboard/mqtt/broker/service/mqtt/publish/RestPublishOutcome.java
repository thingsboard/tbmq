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
package org.thingsboard.mqtt.broker.service.mqtt.publish;

/**
 * Terminal outcome of a REST publish, used as the {@code result} tag of the {@code restPublishMsgs} counter.
 */
public enum RestPublishOutcome {
    /** Accepted by the publish queue (regardless of whether any subscription matched). */
    ACCEPTED("accepted"),
    /** Refused by the total incoming throughput quota. */
    QUOTA_EXCEEDED("quota_exceeded"),
    /** Passed the quota but the publish queue rejected the message. */
    FAILED("failed");

    private final String tagValue;

    RestPublishOutcome(String tagValue) {
        this.tagValue = tagValue;
    }

    public String getTagValue() {
        return tagValue;
    }
}
