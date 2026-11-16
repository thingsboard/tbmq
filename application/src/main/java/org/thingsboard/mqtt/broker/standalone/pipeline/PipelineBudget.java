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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Bounds the publishes inside the pipeline by count and by payload bytes. A publish takes its share when it enters
 * and gives it back when its callback completes, so the budget covers both stages.
 * <p>
 * The counters are written under a lock that only this class takes, for a few instructions, and read without any
 * lock: a worker stuck in a slow store call holds none of it, and the depth gauges stay readable meanwhile.
 */
@Slf4j
@Component
@StandaloneComponent
public class PipelineBudget {

    static final String QUEUED_MESSAGES_GAUGE = "standalonePipelineQueuedMessages";
    static final String QUEUED_BYTES_GAUGE = "standalonePipelineQueuedBytes";

    private final int maxMessages;
    private final long maxBytes;
    private final int pausePercent;
    private final int resumePercent;

    private final Object lock = new Object();
    // written only under the lock, read without it
    private volatile int queuedMessages;
    private volatile long queuedBytes;
    // under the lock: usage reached the pause level and has not yet fallen back to the resume level
    private boolean paused;

    private final List<Runnable> resumeListeners = new CopyOnWriteArrayList<>();
    // a resume holds the read lock and stopResuming the write lock: once stopResuming returns, no resume runs
    private final ReentrantReadWriteLock resumeLock = new ReentrantReadWriteLock();
    private boolean resumesStopped;

    @Autowired
    public PipelineBudget(PipelineProperties properties, StatsFactory statsFactory) {
        this(properties.getPipeline().getMaxMessages(), properties.effectiveMaxBytes(),
                properties.getPipeline().getPausePercent(), properties.getPipeline().getResumePercent());
        statsFactory.createGauge(QUEUED_MESSAGES_GAUGE, this, PipelineBudget::queuedMessages);
        statsFactory.createGauge(QUEUED_BYTES_GAUGE, this, PipelineBudget::queuedBytes);
    }

    PipelineBudget(int maxMessages, long maxBytes, int pausePercent, int resumePercent) {
        if (maxMessages < 1) {
            throw new IllegalArgumentException("standalone.pipeline.max-messages must be at least 1, but was " + maxMessages);
        }
        if (maxBytes < 1) {
            throw new IllegalArgumentException("standalone.pipeline.max-bytes must resolve to at least 1, but was " + maxBytes);
        }
        if (pausePercent < 1 || pausePercent > 100) {
            throw new IllegalArgumentException("standalone.pipeline.pause-percent must be between 1 and 100, but was " + pausePercent);
        }
        if (resumePercent < 1 || resumePercent >= pausePercent) {
            throw new IllegalArgumentException("standalone.pipeline.resume-percent must be at least 1 and below pause-percent ("
                    + pausePercent + "), but was " + resumePercent);
        }
        this.maxMessages = maxMessages;
        this.maxBytes = maxBytes;
        this.pausePercent = pausePercent;
        this.resumePercent = resumePercent;
    }

    /**
     * Takes one message of {@code bytes} bytes. Refused when the message limit is reached or the bytes would go
     * over the byte limit. An empty pipeline always admits one message, so a message larger than the whole byte
     * budget cannot be refused forever.
     *
     * @return {@code false} when the message was refused and nothing was taken
     */
    public boolean tryAcquire(int bytes) {
        requireNotNegative(bytes);
        synchronized (lock) {
            if (queuedMessages >= maxMessages) {
                return false;
            }
            if (queuedMessages > 0 && queuedBytes + bytes > maxBytes) {
                return false;
            }
            take(bytes);
            return true;
        }
    }

    /**
     * @return {@code true} if {@link #tryAcquire} would take a message of {@code bytes} bytes now; nothing is taken
     */
    public boolean hasRoom(int bytes) {
        requireNotNegative(bytes);
        synchronized (lock) {
            return queuedMessages < maxMessages && (queuedMessages == 0 || queuedBytes + bytes <= maxBytes);
        }
    }

    /**
     * Takes one message of {@code bytes} bytes even when that goes over a limit. It is for a publish whose effects
     * were applied before it reached the pipeline, so a refusal could no longer undo them.
     */
    public void acquire(int bytes) {
        requireNotNegative(bytes);
        synchronized (lock) {
            take(bytes);
        }
    }

    // called with the lock held
    private void take(int bytes) {
        queuedMessages++;
        queuedBytes += bytes;
        if (!paused && abovePause()) {
            paused = true;
        }
    }

    /**
     * Gives one message of {@code bytes} bytes back. Neither counter goes below 0. Resume listeners run on this
     * thread when the release brings the usage down to the resume level.
     */
    public void release(int bytes) {
        requireNotNegative(bytes);
        boolean resumed = false;
        synchronized (lock) {
            queuedMessages = Math.max(0, queuedMessages - 1);
            queuedBytes = Math.max(0L, queuedBytes - bytes);
            if (paused && atOrBelowResume()) {
                paused = false;
                resumed = true;
            }
        }
        if (resumed) {
            notifyResumed();
        }
    }

    /**
     * @return {@code true} when messages or bytes are at or above the pause level
     */
    public boolean abovePause() {
        return queuedMessages * 100L >= (long) maxMessages * pausePercent
                || queuedBytes * 100L >= maxBytes * pausePercent;
    }

    public int queuedMessages() {
        return queuedMessages;
    }

    public long queuedBytes() {
        return queuedBytes;
    }

    /**
     * Registers a listener that runs each time the usage falls to or below the resume level after having been at or
     * above the pause level. It runs on the thread that releases, so it must be cheap and must not throw; one that
     * throws, even an {@link Error}, is logged and the others still run. No listener runs after {@link #stopResuming}.
     */
    public void onResume(Runnable listener) {
        resumeListeners.add(listener);
    }

    /**
     * Stops running the resume listeners and the {@link #resumeUnlessPaused} resumes for good, and waits for the ones
     * running now to return. The shutdown calls it before it stops reading from the clients, so the releases of the
     * drain cannot start reading again.
     */
    public void stopResuming() {
        resumeLock.writeLock().lock();
        try {
            resumesStopped = true;
        } finally {
            resumeLock.writeLock().unlock();
        }
    }

    /**
     * Runs {@code resume} unless the usage is still paused (it reached the pause level and has not fallen back to the
     * resume level since) or {@link #stopResuming} was called. It is for a caller that paused something after it saw
     * the usage at the pause level, since a resume that ran between the check and the pause did not see it. While the
     * usage is still paused, its next resume runs after this call. Like a resume listener, {@code resume} must be cheap
     * and must not throw, and {@link #stopResuming} waits for it to return.
     */
    public void resumeUnlessPaused(Runnable resume) {
        runUnlessResumingStopped(() -> {
            boolean stillPaused;
            synchronized (lock) {
                stillPaused = paused;
            }
            if (!stillPaused) {
                resume.run();
            }
        });
    }

    private boolean atOrBelowResume() {
        return queuedMessages * 100L <= (long) maxMessages * resumePercent
                && queuedBytes * 100L <= maxBytes * resumePercent;
    }

    private void notifyResumed() {
        runUnlessResumingStopped(() -> {
            for (Runnable listener : resumeListeners) {
                try {
                    listener.run();
                } catch (Throwable t) {
                    // an Error too: the release must return, so that the publish it belongs to is still answered
                    log.error("Pipeline resume listener failed", t);
                }
            }
        });
    }

    // stopResuming takes the write lock, so it waits for the action to return, and no action runs after it
    private void runUnlessResumingStopped(Runnable action) {
        resumeLock.readLock().lock();
        try {
            if (!resumesStopped) {
                action.run();
            }
        } finally {
            resumeLock.readLock().unlock();
        }
    }

    private static void requireNotNegative(int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative, but was " + bytes);
        }
    }

}
