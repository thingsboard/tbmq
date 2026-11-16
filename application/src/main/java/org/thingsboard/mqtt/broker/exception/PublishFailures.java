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
package org.thingsboard.mqtt.broker.exception;

/**
 * Classifies the failure a publish callback gets, so that every caller of the publish queue handles the same failure
 * the same way.
 */
public final class PublishFailures {

    private PublishFailures() {
    }

    /**
     * @return {@code true} if the broker refused the publish because it is at its limit; an expected outcome under
     * load, logged without a stack trace
     */
    public static boolean isLimitExceeded(Throwable t) {
        return t instanceof PublishLimitExceededException;
    }

    /**
     * @return {@code true} if the publish reached the publish pipeline after it stopped accepting on shutdown; the
     * publish was never published, and this is expected while the broker shuts down
     */
    public static boolean isPipelineClosed(Throwable t) {
        return t instanceof PipelineClosedException;
    }

    /**
     * @return {@code true} for either expected outcome, which needs no stack trace in the log
     */
    public static boolean isExpected(Throwable t) {
        return isLimitExceeded(t) || isPipelineClosed(t);
    }

}
