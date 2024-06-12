/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.stats;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.StringUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.STATS_NAME_TAG;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_MSGS;

@Service
@RequiredArgsConstructor
public class DefaultStatsFactory implements StatsFactory {

    private final MeterRegistry meterRegistry;

    @Value("${metrics.timer.percentiles:0.5}")
    private String timerPercentilesStr;

    private double[] timerPercentiles;

    @PostConstruct
    public void init() {
        if (!StringUtils.isEmpty(timerPercentilesStr)) {
            String[] split = timerPercentilesStr.split(",");
            timerPercentiles = new double[split.length];
            for (int i = 0; i < split.length; i++) {
                timerPercentiles[i] = Double.parseDouble(split[i]);
            }
        }
    }


    @Override
    public StatsCounter createStatsCounter(String key, String statsName, String... tags) {
        String[] updatedTags = new String[tags.length + 2];
        System.arraycopy(tags, 0, updatedTags, 0, tags.length);
        updatedTags[tags.length] = STATS_NAME_TAG;
        updatedTags[tags.length + 1] = statsName;
        return new StatsCounter(
                new AtomicInteger(0),
                meterRegistry.counter(key, updatedTags),
                statsName
        );
    }

    @Override
    public DefaultCounter createDefaultCounter(String key, String... tags) {
        return new DefaultCounter(
                new AtomicInteger(0),
                meterRegistry.counter(key, tags)
        );
    }

    @Override
    public <T extends Number> T createGauge(String key, T number, String... tags) {
        return meterRegistry.gauge(key, Tags.of(tags), number);
    }

    @Override
    public <T> T createGauge(String key, T stateObject, ToDoubleFunction<T> valueFunction, String... tags) {
        return meterRegistry.gauge(key, Tags.of(tags), stateObject, valueFunction);
    }

    @Override
    public MessagesStats createMessagesStats(String key, String... tags) {
        StatsCounter totalCounter = createStatsCounter(key, TOTAL_MSGS, tags);
        StatsCounter successfulCounter = createStatsCounter(key, SUCCESSFUL_MSGS, tags);
        StatsCounter failedCounter = createStatsCounter(key, FAILED_MSGS, tags);
        String statsName = tags.length > 0 ?
                key + "_" + String.join("_", tags) : key;
        return new DefaultMessagesStats(statsName, totalCounter, successfulCounter, failedCounter);
    }

    @Override
    public Timer createTimer(String key, String... tags) {
        Timer.Builder timerBuilder = Timer.builder(key)
                .tags(tags)
                .publishPercentiles();
        if (timerPercentiles != null && timerPercentiles.length > 0) {
            timerBuilder.publishPercentiles(timerPercentiles);
        }
        return timerBuilder.register(meterRegistry);
    }
}
