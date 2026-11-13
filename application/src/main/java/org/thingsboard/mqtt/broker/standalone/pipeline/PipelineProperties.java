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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

/**
 * The {@code standalone.*} settings of the publish pipeline. Only the keys the pipeline reads are bound here; the
 * other keys under {@code standalone} belong to the beans that read them.
 */
@Data
@Component
@StandaloneComponent
@ConfigurationProperties(prefix = "standalone")
public class PipelineProperties {

    private static final int MIN_PARTITIONS = 2;
    private static final int DEFAULT_MAX_BYTES_HEAP_PERCENT = 10;

    private Dispatch dispatch = new Dispatch();
    private Persist persist = new Persist();
    private Pipeline pipeline = new Pipeline();
    private Shutdown shutdown = new Shutdown();

    /**
     * @return the number of dispatch workers: the configured count, or the available processors (at least 2) when it is 0
     */
    public int effectiveDispatchPartitions() {
        return effectivePartitions("standalone.dispatch.partitions", dispatch.partitions);
    }

    /**
     * @return the number of persist workers: the configured count, or the available processors (at least 2) when it is 0
     */
    public int effectivePersistPartitions() {
        return effectivePartitions("standalone.persist.partitions", persist.partitions);
    }

    /**
     * @return the configured byte budget, or 10% of the maximum heap when it is 0
     */
    public long effectiveMaxBytes() {
        if (pipeline.maxBytes < 0) {
            throw new IllegalArgumentException("standalone.pipeline.max-bytes must not be negative, but was " + pipeline.maxBytes);
        }
        if (pipeline.maxBytes > 0) {
            return pipeline.maxBytes;
        }
        return Math.max(1, Runtime.getRuntime().maxMemory() / 100 * DEFAULT_MAX_BYTES_HEAP_PERCENT);
    }

    /**
     * @return the capacity of each persist worker's queue; at least 1, because the queues bound the persist stage
     */
    public int persistPartitionQueueCapacity() {
        if (persist.partitionQueueCapacity < 1) {
            throw new IllegalArgumentException("standalone.persist.partition-queue-capacity must be at least 1, but was "
                    + persist.partitionQueueCapacity);
        }
        return persist.partitionQueueCapacity;
    }

    private static int effectivePartitions(String property, int configured) {
        if (configured < 0) {
            throw new IllegalArgumentException(property + " must not be negative, but was " + configured);
        }
        return configured > 0 ? configured : Math.max(MIN_PARTITIONS, Runtime.getRuntime().availableProcessors());
    }

    @Data
    public static class Dispatch {
        private int partitions = 0;
    }

    @Data
    public static class Persist {
        private int partitions = 0;
        private int batchSize = 256;
        private int partitionQueueCapacity = 4096;
    }

    @Data
    public static class Pipeline {
        private long maxBytes = 0;
        private int maxMessages = 100000;
        private int pausePercent = 80;
        private int resumePercent = 50;
    }

    @Data
    public static class Shutdown {
        private long drainTimeoutMs = 4000;
    }

}
