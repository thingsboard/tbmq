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
package org.thingsboard.mqtt.broker.common.data.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientLifecycleEventTypeUtil {

    /**
     * Integration configuration JSON key holding the opted-in lifecycle event types.
     */
    public static final String LIFECYCLE_EVENT_TYPES_KEY = "lifecycleEventTypes";

    /**
     * Returns {@code true} when the given integration configuration opts in for client lifecycle events,
     * i.e. it declares a non-empty {@link #LIFECYCLE_EVENT_TYPES_KEY} value. An integration that opts in
     * needs its dedicated events topic provisioned and gets its ids cached for event publishing.
     * <p>
     * Deliberately does not require an array: {@code IntegrationServiceImpl.validateDataImpl} rejects a
     * non-array on save, so anything else can only reach here from an already stored or externally supplied
     * configuration, and treating it as opted in keeps such a configuration validated rather than silently
     * skipped. Absent, null and empty container values read as opted out - and so does a bare scalar (e.g. a
     * string or number), since Jackson's {@code ValueNode.isEmpty()} unconditionally returns {@code true}
     * regardless of content. Only a non-empty array or object opts in.
     */
    public static boolean isOptedIn(JsonNode configuration) {
        return configuration != null
                && configuration.has(LIFECYCLE_EVENT_TYPES_KEY)
                && !configuration.get(LIFECYCLE_EVENT_TYPES_KEY).isEmpty();
    }

    /**
     * Null-safe {@link #isOptedIn(JsonNode)} for an integration that may not have been initialized yet, so the
     * Integration Executor shares one definition of the predicate with the broker.
     */
    public static boolean isOptedIn(IntegrationLifecycleMsg lifecycleMsg) {
        return lifecycleMsg != null && isOptedIn(lifecycleMsg.getConfiguration());
    }

    /**
     * Parses event type names into {@link ClientLifecycleEventType} values, invoking {@code onUnknown}
     * for every name that does not map to a known type. The callback decides how to react to an unknown
     * value (e.g. log a warning and skip it, or throw a validation exception).
     */
    public static Set<ClientLifecycleEventType> parse(Iterable<String> names, Consumer<String> onUnknown) {
        Set<ClientLifecycleEventType> eventTypes = new HashSet<>();
        if (names == null) {
            return eventTypes;
        }
        for (String name : names) {
            try {
                eventTypes.add(ClientLifecycleEventType.valueOf(name));
            } catch (IllegalArgumentException e) {
                onUnknown.accept(name);
            }
        }
        return eventTypes;
    }

    /**
     * Parses a JSON array of event type names. See {@link #parse(Iterable, Consumer)}.
     */
    public static Set<ClientLifecycleEventType> parse(JsonNode arrayNode, Consumer<String> onUnknown) {
        if (arrayNode == null) {
            return new HashSet<>();
        }
        List<String> names = new ArrayList<>();
        arrayNode.forEach(node -> names.add(node.asText()));
        return parse(names, onUnknown);
    }

    /**
     * Returns the {@link ClientLifecycleEventType} matching the given name, or {@code null} if the name does not
     * map to a known type (e.g. an event produced by a newer node). Lets callers handle unknown types gracefully
     * instead of throwing.
     */
    public static ClientLifecycleEventType fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ClientLifecycleEventType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
