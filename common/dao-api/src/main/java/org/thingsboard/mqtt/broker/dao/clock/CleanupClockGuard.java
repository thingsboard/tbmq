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
package org.thingsboard.mqtt.broker.dao.clock;

/**
 * Whether the age-based cleanup jobs may run now. Each scheduled run asks first and skips itself when the answer is no.
 * The standalone edition says no while the wall clock is behind the time it last recorded, since ages computed from
 * such a clock are wrong; the cluster edition always says yes.
 */
@FunctionalInterface
public interface CleanupClockGuard {

    /**
     * @return whether an age-based cleanup may run now
     */
    boolean cleanupAllowed();

}
