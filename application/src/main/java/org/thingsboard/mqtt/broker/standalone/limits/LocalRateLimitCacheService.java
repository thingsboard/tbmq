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
package org.thingsboard.mqtt.broker.standalone.limits;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-node {@link RateLimitCacheService}: the client counters are plain atomics and the message buckets are local
 * bucket4j buckets, so nothing here needs Valkey.
 */
@Slf4j
@Service
@StandaloneComponent
public class LocalRateLimitCacheService implements RateLimitCacheService {

    // Must equal the @Bean method names in DevicePersistedMsgsRateLimitsConfiguration / TotalMsgsRateLimitsConfiguration.
    static final String DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN = "devicePersistedMsgsBucketConfiguration";
    static final String TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN = "totalMsgsBucketConfiguration";

    private final BucketConfiguration devicePersistedMsgsBucketConfiguration;
    private final BucketConfiguration totalMsgsBucketConfiguration;
    private final ClientsLimitProperties clientsLimitProperties;
    private final DevicePersistedMsgsRateLimitsConfiguration devicePersistedMsgsRateLimitsConfiguration;
    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;

    private final AtomicLong sessionCount = new AtomicLong();
    private final AtomicLong applicationClientsCount = new AtomicLong();

    private Bucket devicePersistedMsgsBucket;
    private Bucket totalMsgsBucket;

    public LocalRateLimitCacheService(@Autowired(required = false)
                                      @Qualifier(DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN)
                                      BucketConfiguration devicePersistedMsgsBucketConfiguration,
                                      @Autowired(required = false)
                                      @Qualifier(TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN)
                                      BucketConfiguration totalMsgsBucketConfiguration,
                                      ClientsLimitProperties clientsLimitProperties,
                                      DevicePersistedMsgsRateLimitsConfiguration devicePersistedMsgsRateLimitsConfiguration,
                                      TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration) {
        this.devicePersistedMsgsBucketConfiguration = devicePersistedMsgsBucketConfiguration;
        this.totalMsgsBucketConfiguration = totalMsgsBucketConfiguration;
        this.clientsLimitProperties = clientsLimitProperties;
        this.devicePersistedMsgsRateLimitsConfiguration = devicePersistedMsgsRateLimitsConfiguration;
        this.totalMsgsRateLimitsConfiguration = totalMsgsRateLimitsConfiguration;
    }

    @PostConstruct
    public void init() {
        verifyBucketConfigurationPresent(devicePersistedMsgsRateLimitsConfiguration.isEnabled(),
                devicePersistedMsgsBucketConfiguration,
                "mqtt.rate-limits.device-persisted-messages", DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN);
        verifyBucketConfigurationPresent(totalMsgsRateLimitsConfiguration.isEnabled(),
                totalMsgsBucketConfiguration,
                "mqtt.rate-limits.total", TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN);

        this.devicePersistedMsgsBucket = buildBucket(devicePersistedMsgsBucketConfiguration, "device persisted messages");
        this.totalMsgsBucket = buildBucket(totalMsgsBucketConfiguration, "total messages");
    }

    /**
     * The enabled flag and the {@link BucketConfiguration} bean are two independent derivations of the same
     * {@code mqtt.rate-limits.*.enabled} property, the bean reaching the constructor by name. If they disagree, callers
     * pass their {@code isXxxLimitEnabled()} guard and then meter against a null bucket (an NPE per published
     * message), so refuse to start instead.
     */
    private static void verifyBucketConfigurationPresent(boolean limitEnabled, BucketConfiguration configuration,
                                                         String propertyPrefix, String expectedBeanName) {
        if (limitEnabled && configuration == null) {
            throw new IllegalStateException(propertyPrefix + ".enabled is true but no BucketConfiguration was injected;"
                    + " expected a bean named '" + expectedBeanName + "'");
        }
    }

    private static Bucket buildBucket(BucketConfiguration configuration, String name) {
        if (configuration == null) {
            log.info("The {} rate limit is disabled", name);
            return null;
        }
        var builder = Bucket.builder();
        for (Bandwidth bandwidth : configuration.getBandwidths()) {
            builder.addLimit(bandwidth);
        }
        log.info("Configured {} rate limit configuration: {}", name, configuration);
        return builder.build();
    }

    @Override
    public void initSessionCount(int count) {
        sessionCount.set(count);
        if (clientsLimitProperties.isSessionsLimitDisabled()) {
            return;
        }
        log.info("Client session count is initialized with {}", count);
    }

    @Override
    public void initApplicationClientsCount(int count) {
        applicationClientsCount.set(count);
        if (clientsLimitProperties.isApplicationClientsLimitDisabled()) {
            return;
        }
        log.info("Application clients count is initialized with {}", count);
    }

    @Override
    public long incrementSessionCount() {
        return sessionCount.incrementAndGet();
    }

    @Override
    public long incrementApplicationClientsCount() {
        return applicationClientsCount.incrementAndGet();
    }

    @Override
    public void decrementSessionCount() {
        if (clientsLimitProperties.isSessionsLimitDisabled()) {
            return;
        }
        sessionCount.decrementAndGet();
    }

    @Override
    public void decrementApplicationClientsCount() {
        if (clientsLimitProperties.isApplicationClientsLimitDisabled()) {
            return;
        }
        applicationClientsCount.decrementAndGet();
    }

    @Override
    public long tryConsumeDevicePersistedMsgs(long limit) {
        return tryConsume(devicePersistedMsgsBucket, limit);
    }

    @Override
    public long tryConsumeTotalMsgs(long limit) {
        return tryConsume(totalMsgsBucket, limit);
    }

    @Override
    public void returnTotalMsgs(long tokens) {
        if (tokens <= 0 || totalMsgsBucket == null) {
            return;
        }
        totalMsgsBucket.addTokens(tokens);
    }

    private static long tryConsume(Bucket bucket, long limit) {
        if (limit <= 0) {
            return 0;
        }
        return bucket.tryConsumeAsMuchAsPossible(limit);
    }

}
