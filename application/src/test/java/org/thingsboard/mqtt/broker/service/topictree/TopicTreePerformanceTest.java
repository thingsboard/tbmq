/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.service.topictree;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;

import static org.assertj.core.api.Assertions.assertThat;

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
        long cold = ms(() -> s.children(s.getRoot(), new PageLink(100, 0)).getData().forEach(s::clientCounts));
        long warm = ms(() -> s.children(s.getRoot(), new PageLink(100, 0)).getData().forEach(s::clientCounts));
        long devices = ms(() -> s.children(s.find("devices"), new PageLink(100, 0)));
        long search = ms(() -> s.search("telemetry1", new PageLink(100, 0)));
        log.info("subs={} nodes={} build={}ms cold={}ms warm={}ms devicesLevel={}ms search={}ms",
                subs, s.nodesCount(), build, cold, warm, devices, search);
        assertThat(subs).isGreaterThan(900_000);
        assertThat(build).isLessThan(2_000);
        assertThat(cold).isLessThan(1_500);
        assertThat(warm).isLessThan(20);
        assertThat(search).isLessThan(100);
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
