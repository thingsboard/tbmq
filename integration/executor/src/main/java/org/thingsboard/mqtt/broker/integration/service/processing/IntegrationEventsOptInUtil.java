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
package org.thingsboard.mqtt.broker.integration.service.processing;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventTypeUtil;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntegrationEventsOptInUtil {

    /**
     * Null-safe adapter over {@link ClientLifecycleEventTypeUtil#isOptedIn(JsonNode)} for an integration that may
     * not have been initialized yet, so the opt-in predicate stays defined in a single place.
     */
    public static boolean isOptedIn(IntegrationLifecycleMsg lifecycleMsg) {
        return lifecycleMsg != null && ClientLifecycleEventTypeUtil.isOptedIn(lifecycleMsg.getConfiguration());
    }
}
