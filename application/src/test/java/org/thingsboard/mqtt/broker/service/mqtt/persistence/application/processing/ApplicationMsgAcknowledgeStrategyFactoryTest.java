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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit characterization of the ACK strategies behind APPLICATION persisted-message processing.
 * <p>
 * These tests pin down the building blocks of the GH#320 investigation:
 * <ul>
 *     <li>{@link #retryStrategy_whenSameInstanceReused_givesUpAfterConfiguredRetries()} — the
 *     {@code RETRY_ALL} strategy is correct <em>in isolation</em>: one instance counts attempts and
 *     gives up after the configured cap.</li>
 *     <li>{@link #retryStrategy_whenRecreatedForEachAttempt_neverGivesUp()} — recreating the strategy
 *     for every retry (exactly what {@code ApplicationPersistenceProcessorImpl#tryCommitPack} does)
 *     resets the counter, so the cap is dead and retries are effectively unbounded.</li>
 *     <li>{@link #skipStrategy_commitsImmediatelyEvenWithPendingMessages()} — {@code SKIP_ALL} is
 *     at-most-once by design: it always commits, intentionally dropping un-acked messages.</li>
 * </ul>
 */
class ApplicationMsgAcknowledgeStrategyFactoryTest {

    private static final String CLIENT_ID = "appClient";

    private ApplicationMsgAcknowledgeStrategyFactory factory(AckStrategyType type, int retries) {
        ApplicationAckStrategyConfiguration config = new ApplicationAckStrategyConfiguration();
        config.setType(type);
        config.setRetries(retries);
        return new ApplicationMsgAcknowledgeStrategyFactory(config);
    }

    private ApplicationPackProcessingResult resultWithPendingPubRel() {
        ApplicationPackProcessingCtx ctx = new ApplicationPackProcessingCtx(CLIENT_ID);
        ctx.getPubRelPendingMsgMap().put(1, new PersistedPubRelMsg(1, 0L));
        return new ApplicationPackProcessingResult(ctx);
    }

    @Test
    void retryStrategy_whenSameInstanceReused_givesUpAfterConfiguredRetries() {
        ApplicationAckStrategy strategy = factory(AckStrategyType.RETRY_ALL, 2).newInstance(CLIENT_ID);

        assertThat(strategy.analyze(resultWithPendingPubRel()).isCommit())
                .as("attempt 1 must reprocess, not commit").isFalse();
        assertThat(strategy.analyze(resultWithPendingPubRel()).isCommit())
                .as("attempt 2 must reprocess, not commit").isFalse();
        assertThat(strategy.analyze(resultWithPendingPubRel()).isCommit())
                .as("attempt 3 exceeds the cap of 2 retries -> strategy must give up and commit").isTrue();
    }

    @Test
    void retryStrategy_whenRecreatedForEachAttempt_neverGivesUp() {
        ApplicationMsgAcknowledgeStrategyFactory factory = factory(AckStrategyType.RETRY_ALL, 2);

        boolean gaveUp = false;
        for (int attempt = 1; attempt <= 100; attempt++) {
            ApplicationAckStrategy freshStrategyPerAttempt = factory.newInstance(CLIENT_ID);
            if (freshStrategyPerAttempt.analyze(resultWithPendingPubRel()).isCommit()) {
                gaveUp = true;
                break;
            }
        }
        assertThat(gaveUp)
                .as("a fresh RetryStrategy per attempt resets retryCount, so the configured retry cap is never reached")
                .isFalse();
    }

    @Test
    void skipStrategy_commitsImmediatelyEvenWithPendingMessages() {
        ApplicationAckStrategy strategy = factory(AckStrategyType.SKIP_ALL, 0).newInstance(CLIENT_ID);

        assertThat(strategy.analyze(resultWithPendingPubRel()).isCommit())
                .as("SKIP_ALL commits the pack on the first analysis regardless of pending acks (at-most-once by design)")
                .isTrue();
    }
}
