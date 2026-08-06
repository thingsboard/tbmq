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
package org.thingsboard.mqtt.broker.service.limits;

/**
 * Edition seam for the total throughput quota (spec §4.3). CE derives the sustained rate from the
 * mqtt.rate-limits.total.config bandwidths; PE overrides this with the licensed message rate.
 * The Redis bucket configuration itself keeps flowing through the existing
 * totalMsgsBucketConfiguration bean — this seam only feeds block-size auto-sizing.
 */
public interface ThroughputLimitProvider {

    long getSustainedRatePerSec();
}
