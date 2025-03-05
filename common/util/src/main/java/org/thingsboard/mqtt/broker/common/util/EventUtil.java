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
package org.thingsboard.mqtt.broker.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

public class EventUtil {

    public static JsonNode toBodyJson(String serviceId, ComponentLifecycleEvent event, Optional<Exception> e) {
        ObjectNode node = JacksonUtil.newObjectNode().put("server", serviceId).put("event", event.name());
        if (e.isPresent()) {
            node = node.put("success", false);
            node = node.put("error", toString(e.get()));
        } else {
            node = node.put("success", true);
        }
        return node;
    }

    public static JsonNode toBodyJson(String serviceId, long messagesProcessed, long errorsOccurred) {
        return JacksonUtil.newObjectNode().put("server", serviceId).put("messagesProcessed", messagesProcessed).put("errorsOccurred", errorsOccurred);
    }

    public static String toString(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
