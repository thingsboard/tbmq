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
package org.thingsboard.mqtt.broker.service.topictree;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wall-clock and heap budgets on synthetic trees. Excluded from the default build by the {@code performance} tag
 * (see {@code test.excluded.groups} in application/pom.xml); run it on demand with
 * <pre>mvn -pl application test -Dtest=TopicTreePerformanceTest -Dtest.excluded.groups=none</pre>
 */
@Slf4j
@Tag("performance")
class TopicTreePerformanceTest {

    static long ms(Runnable r) {
        long t = System.nanoTime();
        r.run();
        return (System.nanoTime() - t) / 1_000_000;
    }

    @Test
    void budgets() {
        TopicTreeFixtures.Data d = TopicTreeFixtures.synthetic(450_000, 3_000, 5_000);
        TopicTreeSnapshot[] holder = new TopicTreeSnapshot[1];
        TopicTreeSnapshotBuilder.build(d.subs(), d.retained(), d.sessions(), 0);
        long build = ms(() -> holder[0] = TopicTreeSnapshotBuilder.build(d.subs(), d.retained(), d.sessions(), 0));
        TopicTreeSnapshot s = holder[0];
        long subs = s.getRoot().subscriptionsTotal();
        PageLink defaultSort = new PageLink(100, 0, null, new SortOrder("subscriptions", SortOrder.Direction.DESC));
        long cold = ms(() -> s.children(s.getRoot(), new PageLink(100, 0)).getData().forEach(s::clientCounts));
        long warm = ms(() -> s.children(s.getRoot(), new PageLink(100, 0)).getData().forEach(s::clientCounts));
        long devices = ms(() -> s.children(s.find("devices"), new PageLink(100, 0)));
        long devicesSorted = ms(() -> s.children(s.find("devices"), defaultSort));
        long search = ms(() -> s.query(TopicTreeQuery.text("telemetry1"), new PageLink(100, 0)));
        long prefix = ms(() -> s.query(TopicTreeQuery.builder().topic("fleet/eu").topicMatch(TopicTreeQuery.TopicMatch.PREFIX).build(), new PageLink(100, 0)));
        long subscriptions = ms(() -> s.subscriptions(s.find("devices"), new PageLink(20, 5)));
        log.info("subs={} nodes={} build={}ms cold={}ms warm={}ms devicesLevel={}ms devicesLevelSorted={}ms search={}ms prefix={}ms subscriptionsPage={}ms",
                subs, s.nodesCount(), build, cold, warm, devices, devicesSorted, search, prefix, subscriptions);
        assertThat(subs).isGreaterThan(900_000);
        assertThat(build).isLessThan(2_000);
        assertThat(cold).isLessThan(1_500);
        assertThat(warm).isLessThan(20);
        assertThat(devicesSorted).isLessThan(50);
        assertThat(search).isLessThan(100);
        assertThat(prefix).isLessThan(100);
        assertThat(subscriptions).isLessThan(50);
    }

    @Test
    void heap() {
        TopicTreeFixtures.Data spec = TopicTreeFixtures.fanOut(30_000, 3_000, 5_000, 1_000_000);
        long specBytes = retainedBytes(spec);
        TopicTreeFixtures.Data large = TopicTreeFixtures.synthetic(450_000, 3_000, 5_000);
        retainedBytes(large);
        assertThat(specBytes).isLessThan(150L * 1024 * 1024);
    }

    private static long retainedBytes(TopicTreeFixtures.Data d) {
        long before = usedAfterGc();
        TopicTreeSnapshot s = TopicTreeSnapshotBuilder.build(d.subs(), d.retained(), d.sessions(), 0);
        long bytes = usedAfterGc() - before;
        log.info("heap subs={} nodes={} retained={} MB", s.getRoot().subscriptionsTotal(), s.nodesCount(), bytes / (1024 * 1024));
        return bytes;
    }

    private static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        long used = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            used = Math.min(used, rt.totalMemory() - rt.freeMemory());
        }
        return used;
    }
}
