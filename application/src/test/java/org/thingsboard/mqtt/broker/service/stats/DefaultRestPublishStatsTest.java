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
package org.thingsboard.mqtt.broker.service.stats;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishOutcome;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRestPublishStatsTest {

    private SimpleMeterRegistry meterRegistry;
    private DefaultRestPublishStats stats;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        stats = new DefaultRestPublishStats(new DefaultStatsFactory(meterRegistry));
    }

    @Test
    void givenOutcomes_whenIncrement_thenEachTaggedCounterCountsSeparately() {
        stats.increment(RestPublishOutcome.ACCEPTED);
        stats.increment(RestPublishOutcome.ACCEPTED);
        stats.increment(RestPublishOutcome.QUOTA_EXCEEDED);

        assertThat(stats.getCount(RestPublishOutcome.ACCEPTED)).isEqualTo(2);
        assertThat(stats.getCount(RestPublishOutcome.QUOTA_EXCEEDED)).isEqualTo(1);
        assertThat(stats.getCount(RestPublishOutcome.FAILED)).isZero();
        assertThat(micrometerCount("accepted")).isEqualTo(2.0);
        assertThat(micrometerCount("quota_exceeded")).isEqualTo(1.0);
        assertThat(micrometerCount("failed")).isZero();
    }

    @Test
    void givenCounts_whenReset_thenIntervalCountsClearButPrometheusCountersStayMonotonic() {
        stats.increment(RestPublishOutcome.FAILED);

        stats.reset();

        assertThat(stats.getCount(RestPublishOutcome.FAILED)).isZero();
        assertThat(micrometerCount("failed")).isEqualTo(1.0);
    }

    private double micrometerCount(String result) {
        return meterRegistry.get(StatsType.REST_PUBLISH_MSGS.getPrintName()).tag(DefaultRestPublishStats.RESULT_TAG, result).counter().count();
    }

}
