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
package org.thingsboard.mqtt.broker.service.stats;

public class StubDroppedLifecycleEventStats implements DroppedLifecycleEventStats {

    public static final StubDroppedLifecycleEventStats STUB_DROPPED_LIFECYCLE_EVENT_STATS = new StubDroppedLifecycleEventStats();

    @Override
    public void increment() {
    }

    @Override
    public void increment(int count) {
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public void reset() {
    }
}
