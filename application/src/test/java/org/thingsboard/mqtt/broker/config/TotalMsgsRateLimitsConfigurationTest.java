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
package org.thingsboard.mqtt.broker.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class TotalMsgsRateLimitsConfigurationTest {

    @Test
    public void testTotalMsgsBucketConfiguration() {
        TotalMsgsRateLimitsConfiguration config = new TotalMsgsRateLimitsConfiguration();
        config.setEnabled(true);
        config.setConfig("100:60,200:120");

        BucketConfiguration bucketConfig = config.totalMsgsBucketConfiguration();

        assertNotNull(bucketConfig);
        assertEquals(2, bucketConfig.getBandwidths().length);

        Bandwidth bw1 = bucketConfig.getBandwidths()[0];
        assertEquals(100, bw1.getCapacity());
        assertEquals(TimeUnit.SECONDS.toNanos(60), bw1.getRefillPeriodNanos());

        Bandwidth bw2 = bucketConfig.getBandwidths()[1];
        assertEquals(200, bw2.getCapacity());
        assertEquals(TimeUnit.SECONDS.toNanos(120), bw2.getRefillPeriodNanos());
    }

    @Test
    public void testTotalMsgsBucketConfigurationInvalidFormat() {
        TotalMsgsRateLimitsConfiguration config = new TotalMsgsRateLimitsConfiguration();
        config.setEnabled(true);
        config.setConfig("100:60,200");

        Exception exception = assertThrows(IllegalArgumentException.class, config::totalMsgsBucketConfiguration);
        assertEquals("Invalid limitSrc format: 200", exception.getMessage());
    }

}
