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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import static org.assertj.core.api.Assertions.assertThat;

class PublishFailuresTest {

    @Test
    void aFullDiskIsALimitExceeded() {
        StoreFullException full = new StoreFullException("Not enough free disk space for the store");

        assertThat(PublishFailures.isLimitExceeded(full)).isTrue();
        assertThat(PublishFailures.isExpected(full)).isTrue();
        assertThat(PublishFailures.isPipelineClosed(full)).isFalse();
    }

    @Test
    void anyOtherStoreErrorIsNeitherALimitNorExpected() {
        StoreException failed = new StoreException("Failed to write to the store");

        assertThat(PublishFailures.isLimitExceeded(failed)).isFalse();
        assertThat(PublishFailures.isExpected(failed)).isFalse();
    }

    @Test
    void aFullPublishPipelineStaysALimitExceeded() {
        assertThat(PublishFailures.isLimitExceeded(new PublishLimitExceededException("publish queue is full"))).isTrue();
        assertThat(PublishFailures.isLimitExceeded(new RuntimeException("other"))).isFalse();
    }

}
