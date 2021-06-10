/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.sql;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Builder
public class TbSqlBlockingQueuePool<E> implements TbSqlQueue<E> {
    private final CopyOnWriteArrayList<TbSqlBlockingQueue<E>> queues = new CopyOnWriteArrayList<>();

    private final TbSqlQueueParams params;
    private final int maxThreads;
    private final Function<E, Integer> queueIndexHashFunction;
    private final Consumer<List<E>> processingFunction;
    private final SqlQueueStatsManager statsManager;

    public void init() {
        for (int i = 0; i < maxThreads; i++) {
            MessagesStats queueStats = statsManager.createSqlQueueStats(params.getQueueName(), i);
            TbSqlBlockingQueue<E> queue = new TbSqlBlockingQueue<>(i, params, processingFunction, queueStats);
            queues.add(queue);
            queue.init();
        }
    }

    @Override
    public ListenableFuture<Void> add(E element) {
        int queueIndex = (queueIndexHashFunction.apply(element) & 0x7FFFFFFF) % maxThreads;
        return queues.get(queueIndex).add(element);
    }

    @Override
    public void destroy() {
        queues.forEach(TbSqlBlockingQueue::destroy);
    }
}
