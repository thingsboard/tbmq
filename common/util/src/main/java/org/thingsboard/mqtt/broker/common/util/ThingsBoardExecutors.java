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
package org.thingsboard.mqtt.broker.common.util;

import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class ThingsBoardExecutors {

    /**
     * Method forked from ExecutorService to provide thread poll name
     *
     * Creates a thread pool that maintains enough threads to support
     * the given parallelism level, and may use multiple queues to
     * reduce contention. The parallelism level corresponds to the
     * maximum number of threads actively engaged in, or available to
     * engage in, task processing. The actual number of threads may
     * grow and shrink dynamically. A work-stealing pool makes no
     * guarantees about the order in which submitted tasks are
     * executed.
     *
     * @param parallelism the targeted parallelism level
     * @param namePrefix used to define thread name
     * @return the newly created thread pool
     * @throws IllegalArgumentException if {@code parallelism <= 0}
     * @since 1.8
     */
    public static ExecutorService newWorkStealingPool(int parallelism, String namePrefix) {
        return new ForkJoinPool(parallelism,
                new ThingsBoardForkJoinWorkerThreadFactory(namePrefix),
                null, true);
    }

    public static ExecutorService newWorkStealingPool(int parallelism, Class clazz) {
        return newWorkStealingPool(parallelism, clazz.getSimpleName());
    }

    public static ExecutorService initExecutorService(int threadsCount, String serviceName) {
        if (threadsCount <= 0) {
            threadsCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        }
        if (threadsCount == 1) {
            return Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName(serviceName));
        } else {
            return Executors.newFixedThreadPool(threadsCount, ThingsBoardThreadFactory.forName(serviceName));
        }
    }

    public static ExecutorService initCachedExecutorService(String serviceName) {
        return Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName(serviceName));
    }

    public static ScheduledExecutorService initScheduledExecutorService(int threadsCount, String serviceName) {
        if (threadsCount <= 1) {
            return Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName(serviceName));
        }
        return Executors.newScheduledThreadPool(threadsCount, ThingsBoardThreadFactory.forName(serviceName));
    }

    public static void shutdownAndAwaitTermination(ExecutorService service, String name) {
        shutdownAndAwaitTermination(service, 0, name);
    }

    public static void shutdownAndAwaitTermination(ExecutorService service, long timeoutSeconds, String name) {
        if (service == null) {
            return;
        }
        long seconds = getTimeoutSeconds(timeoutSeconds);
        log.debug("Initiating graceful shutdown of {} executor within {}s", name, seconds);
        boolean terminated = MoreExecutors.shutdownAndAwaitTermination(service, Duration.ofSeconds(seconds));
        log.info("{} executor termination is: [{}]", name, terminated ? "successful" : "failed");
    }

    public static long getTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds > 0) {
            return timeoutSeconds;
        }
        String timeoutValue = System.getenv("TBMQ_GRACEFUL_SHUTDOWN_TIMEOUT_SEC");
        if (timeoutValue == null) {
            timeoutValue = System.getProperty("tbmq.graceful.shutdown.timeout.sec");
        }
        final long defaultTimeout = 5L;
        try {
            return timeoutValue != null ? Long.parseLong(timeoutValue) : defaultTimeout;
        } catch (NumberFormatException e) {
            log.error("Invalid timeout value from system properties {}. Using default: 5 seconds", timeoutValue);
            return defaultTimeout;
        }
    }
}
