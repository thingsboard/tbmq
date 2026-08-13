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
package org.thingsboard.mqtt.broker.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pins the per-publish fan-out ceiling that the SHAPE of the total quota bucket imposes, because under prepaid charging
 * one publish's whole cost is charged as a single bulk consume and bucket4j requires EVERY configured bandwidth to hold
 * the tokens for that consume. The smallest available capacity therefore wins, and no amount of idling accumulates past
 * it.
 * <p>
 * This is a characterisation test of a library behaviour the shipped default and the yml guidance depend on, not a
 * driver for new code. It pins both halves of that guidance: that the single-bandwidth default really does let one
 * publish charge a wide fan-out, and that the multi-bandwidth form the yml warns about really does cap it at the
 * smallest capacity. Neither claim can drift away from what the parser and bucket4j actually do without failing here.
 * <p>
 * Both cases go through {@link AbstractMsgsRateLimitsConfiguration#getBucketConfiguration()} from the config STRING, so
 * what is pinned is our parsing of the value that really ships, not just bucket4j in the abstract.
 */
public class TotalMsgsRateLimitsBucketShapeTest {

    private static final int WIDE_FAN_OUT = 5000;

    @Test
    public void givenTheShippedSingleBandwidthDefault_whenOnePublishChargesAWideFanOut_thenGrantedInFull() {
        Bucket bucket = localBucketFor("50000:60");

        // what ships: ~833 msgs/s sustained with no per-publish ceiling below the configured capacity, so a wide
        // fan-out is bounded by the cluster budget rather than by the bucket's shape
        assertEquals(WIDE_FAN_OUT, bucket.tryConsumeAsMuchAsPossible(WIDE_FAN_OUT));
    }

    @Test
    public void givenAMultiBandwidthConfig_whenOnePublishChargesAWideFanOut_thenCappedByTheTightestBandwidth() {
        Bucket bucket = localBucketFor("1000:1,50000:60");

        // the trap the yml warns about, and why the default is not this: bucket4j takes tokens from EVERY bandwidth,
        // so at the same ~833 msgs/s the tight 1000:1 one caps a single publish at 1000 even on an idle cluster
        assertEquals(1000, bucket.tryConsumeAsMuchAsPossible(WIDE_FAN_OUT));
    }

    private Bucket localBucketFor(String config) {
        TotalMsgsRateLimitsConfiguration configuration = new TotalMsgsRateLimitsConfiguration();
        configuration.setEnabled(true);
        configuration.setConfig(config);

        BucketConfiguration bucketConfiguration = configuration.getBucketConfiguration();
        LocalBucketBuilder builder = Bucket.builder();
        for (Bandwidth bandwidth : bucketConfiguration.getBandwidths()) {
            builder.addLimit(bandwidth);
        }
        return builder.build();
    }
}
