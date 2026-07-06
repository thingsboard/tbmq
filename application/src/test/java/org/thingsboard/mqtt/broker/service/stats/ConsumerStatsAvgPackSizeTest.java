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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

/**
 * The avg-pack-size behavior shared by the three consumer stats implementations, which all delegate
 * to {@link org.thingsboard.mqtt.broker.common.stats.PackSizeStats}. Parameterized over each so the
 * single shared invariant (0 at idle, ceil(avg) after packs, 0 after reset — never NaN) isn't
 * asserted in three near-identical copies.
 */
@RunWith(Parameterized.class)
public class ConsumerStatsAvgPackSizeTest {

    static final class Fixture {
        final IntConsumer recordPack;
        final DoubleSupplier avgPackSize;
        final Runnable reset;

        Fixture(IntConsumer recordPack, DoubleSupplier avgPackSize, Runnable reset) {
            this.recordPack = recordPack;
            this.avgPackSize = avgPackSize;
            this.reset = reset;
        }
    }

    private static StatsFactory newStatsFactory() {
        return new DefaultStatsFactory(new SimpleMeterRegistry());
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"publishMsgConsumer", (Supplier<Fixture>) () -> {
                    DefaultPublishMsgConsumerStats stats = new DefaultPublishMsgConsumerStats("consumer-1", newStatsFactory());
                    return new Fixture(size -> stats.logPackProcessingTime(size, 1, TimeUnit.MILLISECONDS),
                            stats::getAvgPackSize, stats::reset);
                }},
                {"deviceProcessor", (Supplier<Fixture>) () -> {
                    DefaultDeviceProcessorStats stats = new DefaultDeviceProcessorStats("consumer-1", newStatsFactory());
                    return new Fixture(size -> stats.logClientIdPacksProcessingTime(size, 1, TimeUnit.MILLISECONDS),
                            stats::getAvgPackSize, stats::reset);
                }},
                {"clientSessionEventConsumer", (Supplier<Fixture>) () -> {
                    DefaultClientSessionEventConsumerStats stats = new DefaultClientSessionEventConsumerStats("consumer-1", newStatsFactory());
                    return new Fixture(size -> stats.logPackProcessingTime(size, 1, TimeUnit.MILLISECONDS),
                            stats::getAvgPackSize, stats::reset);
                }},
        });
    }

    @Parameterized.Parameter(0)
    public String name;

    @Parameterized.Parameter(1)
    public Supplier<Fixture> fixtureFactory;

    private Fixture fixture;

    @Before
    public void setUp() {
        fixture = fixtureFactory.get();
    }

    @Test
    public void givenNoPacksProcessed_whenGetAvgPackSize_thenReturnsZeroNotNaN() {
        assertEquals(0.0, fixture.avgPackSize.getAsDouble(), 0.0);
    }

    @Test
    public void givenPacksProcessed_whenGetAvgPackSize_thenReturnsCeilOfAverage() {
        fixture.recordPack.accept(3);
        fixture.recordPack.accept(4);

        // (3 + 4) / 2 = 3.5 -> ceil -> 4
        assertEquals(4.0, fixture.avgPackSize.getAsDouble(), 0.0);
    }

    @Test
    public void givenReset_whenGetAvgPackSize_thenReturnsZeroNotNaN() {
        fixture.recordPack.accept(5);

        fixture.reset.run();

        assertEquals(0.0, fixture.avgPackSize.getAsDouble(), 0.0);
    }
}
