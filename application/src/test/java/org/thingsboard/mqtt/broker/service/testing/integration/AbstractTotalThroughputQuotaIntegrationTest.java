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
package org.thingsboard.mqtt.broker.service.testing.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.STATS_NAME_TAG;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_MSGS;

/**
 * Shared scaffolding for the total-throughput-quota integration suites: the droppedMsgs ledger, the shared-bucket
 * drain and the waits every suite needs, leaving each subclass just its own ledger arithmetic.
 * <p>
 * Only the two properties every suite agrees on live here; each picks its own capacity and block size, which a
 * subclass {@code @TestPropertySource} merges over this one. That is also why the suites do not share a Spring
 * context: each reasons about an exact node-local pool and bucket balance, so a context carried over from another
 * class would reintroduce the cross-test coupling these assertions exist to rule out.
 */
@Slf4j
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        // no node may hand its lease back mid-test: every suite reasons about an exact local pool
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
public abstract class AbstractTotalThroughputQuotaIntegrationTest extends AbstractPubSubIntegrationTest {

    protected static final long DRAIN_TOKENS = 10_000;
    protected static final long AWAIT_TIMEOUT_SEC = 30;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);
    private static final Duration SLOW_POLL_INTERVAL = Duration.ofMillis(200);

    /**
     * The one wait the suites cannot turn into an event: how long a negative - nothing MORE may arrive - is given
     * before it is believed. Nothing moves while the contract holds, so only elapsed quiet time can establish it,
     * and every such site pays this same deliberately chosen price rather than an ad-hoc one. It buys confidence
     * rather than proof: a redelivery slower than this would still slip through, which is why the suites also pin
     * the mechanism - committed offsets, an empty store - through the assertions around it.
     */
    private static final Duration SETTLE = Duration.ofMillis(1500);

    @Autowired
    protected MeterRegistry meterRegistry;
    @Autowired
    protected RateLimitCacheService rateLimitCacheService;

    /**
     * The reporting scheduler zeroes the historical droppedMsgs counter; this Micrometer one is monotonic, so
     * suites read a delta against a baseline taken earlier in the same test.
     */
    protected double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }

    /**
     * Empties the shared bucket - a stand-in for another cluster node having spent the budget, perturbing neither
     * droppedMsgs nor any store. Asserts it took something, so a suite whose next assertion is a negative one
     * cannot pass against a bucket that was never drained.
     */
    protected void drainSharedBucket(String reason) {
        long drained = rateLimitCacheService.tryConsumeTotalMsgs(DRAIN_TOKENS);
        log.info("Drained {} tokens from the shared bucket: {}", drained, reason);
        assertTrue("the drain must actually empty the shared bucket", drained > 0);
    }

    /**
     * Same end state as {@link #drainSharedBucket}, for setup running before EVERY test: the second run finds the
     * bucket already empty, so it asserts the state the tests need rather than how much this call took.
     */
    protected void ensureSharedBucketEmpty() {
        rateLimitCacheService.tryConsumeTotalMsgs(DRAIN_TOKENS);
        assertEquals("the shared bucket must be empty before the test publishes",
                0, rateLimitCacheService.tryConsumeTotalMsgs(1));
    }

    /**
     * Matcher form rather than a boolean lambda, so a timeout reports how much did arrive - the size of the loss.
     */
    protected void awaitReceived(String alias, AtomicInteger received, int expected) {
        Awaitility.await(alias)
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .until(received::get, greaterThanOrEqualTo(expected));
    }

    /**
     * Waits for the dispatcher to finish accounting for work already in flight - faster than a fixed sleep, and it
     * does not proceed early on a loaded box.
     */
    protected void awaitDroppedMsgsAtLeast(String alias, double expected) {
        Awaitility.await(alias)
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .until(this::droppedMsgs, greaterThanOrEqualTo(expected));
    }

    /**
     * Publishes the consumer has finished processing, off the monotonic Micrometer counter behind
     * {@code incomingPublishMsg.consumed}. A pack is only logged once every one of its callbacks has resolved, so
     * this moving is proof that a publish has been charged for its whole fan-out AND persisted - the observable
     * that lets the suites wait on the event rather than guess at a duration.
     */
    protected double publishesProcessed() {
        return meterRegistry.find(StatsType.PUBLISH_MSG_CONSUMER.getPrintName())
                .tag(STATS_NAME_TAG, SUCCESSFUL_MSGS)
                .counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    /**
     * Paces a publish loop on the event rather than the clock: the next ingress charge is only made once the
     * previous publish has been charged for its fan-out, which is also what draws the node-local pool back up. The
     * ingress charge never draws itself - it grants on bounded credit and schedules an asynchronous draw - so an
     * unpaced loop can outrun the pool and be refused with the shared bucket still full.
     */
    protected void pacePublish(double processedBefore, int publishIndex) {
        awaitPublishesProcessed("publish " + publishIndex + " charged and stored", processedBefore + publishIndex + 1);
    }

    private void awaitPublishesProcessed(String alias, double expected) {
        Awaitility.await(alias)
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .until(this::publishesProcessed, greaterThanOrEqualTo(expected));
    }

    /**
     * A negative: the count must be at {@code expected} and STAY there for {@link #SETTLE}. Written as an invariant
     * rather than a sleep followed by an assertion, so an overshoot that is corrected before the read cannot slip
     * past, and so a failure reports which wait it was.
     */
    protected void assertNothingMoreArrives(String alias, AtomicInteger received, int expected) {
        Awaitility.await(alias)
                .during(SETTLE)
                .atMost(SETTLE.plusSeconds(AWAIT_TIMEOUT_SEC))
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .until(received::get, equalTo(expected));
    }

    /**
     * Reads a partial delivery whose size is not known in advance, and so has no target count to poll for: waits
     * out an initial quiet window, then requires the count to stop moving. Strictly stronger than reading whatever
     * a fixed sleep happened to leave, because a still-growing delivery keeps the wait going.
     */
    protected int awaitDeliverySettled(String alias, AtomicInteger received) {
        AtomicInteger previous = new AtomicInteger(Integer.MIN_VALUE);
        Awaitility.await(alias)
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .pollDelay(SETTLE)
                .pollInterval(SLOW_POLL_INTERVAL)
                .until(() -> received.get() == previous.getAndSet(received.get()));
        return received.get();
    }

    /**
     * Waits for the shared bucket to hold {@code tokens} again after a drain, probing without spending: whatever a
     * probe takes it hands straight back. The alternative is sleeping the bandwidth's whole refill period, which
     * has to assume the worst case and then pays it on every run.
     */
    protected void awaitSharedBucketRefilled(String alias, long tokens) {
        Awaitility.await(alias)
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .pollDelay(Duration.ZERO)
                .pollInterval(SLOW_POLL_INTERVAL)
                .until(() -> probeSharedBucket(tokens));
    }

    private boolean probeSharedBucket(long tokens) {
        long taken = rateLimitCacheService.tryConsumeTotalMsgs(tokens);
        rateLimitCacheService.returnTotalMsgs(taken);
        return taken == tokens;
    }
}
