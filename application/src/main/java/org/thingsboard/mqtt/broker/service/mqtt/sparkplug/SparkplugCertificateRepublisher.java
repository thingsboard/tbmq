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
package org.thingsboard.mqtt.broker.service.mqtt.sparkplug;

import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

/**
 * Sparkplug 3.0 §10.1.4 — Sparkplug Aware MQTT Server. When an Edge Node publishes an
 * NBIRTH or DBIRTH on the standard {@code spBv1.0/...} namespace (with retain=false per
 * spec), the broker must additionally make the same payload available on the
 * {@code $sparkplug/certificates/...} topic with retain=true so that consumers can read
 * the latest birth certificate at any time.
 */
public interface SparkplugCertificateRepublisher {

    /**
     * If {@code publishMsg} is a Sparkplug B v1.0 NBIRTH or DBIRTH, synchronously
     * stores a copy of the payload on the corresponding {@code $sparkplug/certificates/...}
     * topic in the retained-message store, then asynchronously dispatches it through the
     * standard Kafka pipeline so live subscribers receive it. For any other topic shape
     * this is a no-op. The original publish is not modified — the caller continues to
     * dispatch it through the normal publish path. Any exception raised during the
     * republish is logged and swallowed so the primary publish path is never
     * compromised.
     */
    void maybeRepublish(SessionInfo sessionInfo, PublishMsg publishMsg, String clientCertCn);
}
