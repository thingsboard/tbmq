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
package org.thingsboard.mqtt.broker.service.limits;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.local.LocalBucketBuilder;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "service", value = "singleton-mode", havingValue = "true", matchIfMissing = true)
public class RateLimitLocalCacheServiceImpl extends AbstractRateLimitCacheService implements RateLimitCacheService {

    private final LocalBucket devicePersistedMsgsBucket;
    private final LocalBucket totalMsgsBucket;

    @Getter
    private AtomicLong sessionsCounter;
    @Getter
    private AtomicLong applicationClientsCounter;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;
    @Value("${mqtt.application-clients-limit:0}")
    @Setter
    private int applicationClientsLimit;

    public RateLimitLocalCacheServiceImpl(@Autowired(required = false) BucketConfiguration devicePersistedMsgsBucketConfiguration,
                                          @Autowired(required = false) BucketConfiguration totalMsgsBucketConfiguration) {
        this.devicePersistedMsgsBucket = getLocalBucket(devicePersistedMsgsBucketConfiguration);
        this.totalMsgsBucket = getLocalBucket(totalMsgsBucketConfiguration);
    }

    @PostConstruct
    public void init() {
        if (sessionsLimit > 0) {
            sessionsCounter = new AtomicLong();
        }
        if (applicationClientsLimit > 0) {
            applicationClientsCounter = new AtomicLong();
        }
    }

    @Override
    public void initSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        log.info("Initializing client session limit cache with count {}", count);
        sessionsCounter.set(count);
    }

    @Override
    public void setSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Set session limit cache to {}", count);
        sessionsCounter.set(count);
    }

    @Override
    public void initApplicationClientsCount(int count) {
        if (applicationClientsLimit <= 0) {
            return;
        }
        log.info("Initializing application clients limit cache with count {}", count);
        applicationClientsCounter.set(count);
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        return sessionsCounter.incrementAndGet();
    }

    @Override
    public long incrementApplicationClientsCount() {
        log.debug("Incrementing Application clients count");
        return applicationClientsCounter.incrementAndGet();
    }

    @Override
    public void decrementSessionCount() {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Decrementing session count");
        sessionsCounter.updateAndGet(count -> count > 0 ? count - 1 : 0);
    }

    @Override
    public void decrementApplicationClientsCount() {
        if (applicationClientsLimit <= 0) {
            return;
        }
        log.debug("Decrementing Application clients count");
        applicationClientsCounter.updateAndGet(count -> count > 0 ? count - 1 : 0);
    }

    @Override
    public boolean tryConsumeDevicePersistedMsg() {
        return devicePersistedMsgsBucket.tryConsume(1);
    }

    @Override
    public long tryConsumeAsMuchAsPossibleDevicePersistedMsgs(long limit) {
        return tryConsumeAsMuchAsPossible(devicePersistedMsgsBucket, limit);
    }

    @Override
    public boolean tryConsumeTotalMsg() {
        return totalMsgsBucket.tryConsume(1);
    }

    @Override
    public long tryConsumeAsMuchAsPossibleTotalMsgs(long limit) {
        return tryConsumeAsMuchAsPossible(totalMsgsBucket, limit);
    }

    LocalBucket getLocalBucket(BucketConfiguration bucketConfiguration) {
        if (bucketConfiguration != null) {
            LocalBucketBuilder builder = Bucket.builder();
            for (var bandwidth : bucketConfiguration.getBandwidths()) {
                builder.addLimit(bandwidth);
            }
            return builder.build();
        }
        return null;
    }
}
