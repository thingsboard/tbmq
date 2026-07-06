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
package org.thingsboard.mqtt.broker.common.stats;

/**
 * Formats a {@link MessagesStats} into the counters string used by the periodic stats log lines.
 * Kept out of the {@link MessagesStats} interface so that interface stays a pure recorder/accessor.
 *
 * <p>The {@code queueSize} field is emitted only when the stats tracks a real queue depth
 * ({@link MessagesStats#isQueueSizeTracked()}); producer-style stats (which never wire a supplier)
 * omit the dead always-0 field.
 */
public final class MessagesStatsFormatter {

    private MessagesStatsFormatter() {
    }

    public static String format(MessagesStats stats) {
        StringBuilder sb = new StringBuilder();
        if (stats.isQueueSizeTracked()) {
            sb.append(StatsConstantNames.QUEUE_SIZE).append(" = [").append(stats.getCurrentQueueSize()).append("] ");
        }
        sb.append(StatsConstantNames.TOTAL_MSGS).append(" = [").append(stats.getTotal()).append("] ")
                .append(StatsConstantNames.SUCCESSFUL_MSGS).append(" = [").append(stats.getSuccessful()).append("] ")
                .append(StatsConstantNames.FAILED_MSGS).append(" = [").append(stats.getFailed()).append("] ");
        return sb.toString();
    }
}
