/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import com.google.common.util.concurrent.SettableFuture;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
class TbSqlBlockingQueue<E> implements TbSqlQueue<E> {

    private final BlockingQueue<TbSqlQueueElement<E>> queue = new LinkedBlockingQueue<>();

    @Getter
    private final int id;
    private final TbSqlQueueParams params;
    private final Consumer<List<E>> processingFunction;
    private final MessagesStats stats;
    private final Comparator<E> batchUpdateComparator;

    private ExecutorService executor;
    private volatile boolean stopped = false;

    @Override
    public void init() {
        String queueName = params.getQueueName();
        stats.updateQueueSize(queue::size);
        this.executor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("sql-queue-" + id + "-" + queueName.toLowerCase()));
        executor.execute(() -> processElementsQueue(queueName));
    }

    private void processElementsQueue(String queueName) {
        int batchSize = params.getBatchSize();
        long maxDelay = params.getMaxDelay();
        List<TbSqlQueueElement<E>> elements = new ArrayList<>(batchSize);
        while (!stopped && !Thread.interrupted()) {
            try {
                long currentTs = System.currentTimeMillis();
                TbSqlQueueElement<E> queuedElement = queue.poll(maxDelay, TimeUnit.MILLISECONDS);
                if (queuedElement == null) {
                    continue;
                } else {
                    elements.add(queuedElement);
                }
                queue.drainTo(elements, batchSize - 1);
                boolean fullPack = elements.size() == batchSize;
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Going to process {} elements.", queueName, elements.size());
                }
                Stream<E> elementsStream = elements.stream().map(TbSqlQueueElement::getElement);
                processingFunction.accept(
                        (params.isBatchSortEnabled() ? elementsStream.sorted(batchUpdateComparator) : elementsStream)
                                .collect(Collectors.toList())
                );
                elements.forEach(element -> element.getFuture().set(null));
                stats.incrementSuccessful(elements.size());
                if (!fullPack) {
                    long remainingDelay = maxDelay - (System.currentTimeMillis() - currentTs);
                    if (remainingDelay > 0) {
                        Thread.sleep(remainingDelay);
                    }
                }
            } catch (Exception e) {
                stats.incrementFailed(elements.size());
                elements.forEach(element -> element.getFuture().setException(e));
                if (e instanceof InterruptedException) {
                    log.info("[{}] Queue polling was interrupted.", queueName);
                    break;
                } else {
                    log.error("[{}] Failed to process {} elements.", queueName, elements.size(), e);
                }
            } finally {
                elements.clear();
            }
        }
    }

    @Override
    public void destroy(String name) {
        stopped = true;
        if (executor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(executor, name);
        }
    }

    @Override
    public ListenableFuture<Void> add(E element) {
        SettableFuture<Void> future = SettableFuture.create();
        queue.add(new TbSqlQueueElement<>(future, element));
        stats.incrementTotal();
        return future;
    }
}
