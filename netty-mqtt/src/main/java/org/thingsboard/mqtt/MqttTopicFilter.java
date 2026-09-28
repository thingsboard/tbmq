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
package org.thingsboard.mqtt;

import java.util.Arrays;

/**
 * One compiled MQTT topic filter, matched by level comparison rather than by regex.
 * <p>
 * MQTT 3.1.1 §4.7: {@code +} matches exactly one level, {@code #} matches the level it replaces and every level below
 * it (and must be the last level of the filter - a filter that breaks this can never match anything), every other
 * character is literal, and a filter whose first level is a wildcard never matches a topic beginning with {@code $}.
 * A {@code $share/<group>/} prefix is not part of the filter: the broker delivers the real topic, so the prefix is
 * stripped at compile time.
 */
final class MqttTopicFilter {

    private static final char LEVEL_SEPARATOR = '/';
    private static final String LEVEL_SEPARATOR_STR = String.valueOf(LEVEL_SEPARATOR);
    private static final String SINGLE_LEVEL = "+";
    private static final String MULTI_LEVEL = "#";
    private static final String SHARE_PREFIX = "$share/";
    private static final char DOLLAR = '$';

    /** Never matches anything: a null/empty filter, a malformed {@code $share/} prefix, or a non-terminal '#'. */
    private static final MqttTopicFilter NEVER = new MqttTopicFilter(null, false, false);

    /**
     * The filter's levels past any {@code $share/<group>/} prefix and past a trailing '#', or null for a filter
     * that can never match (see {@link #NEVER}).
     */
    private final String[] levels;
    private final boolean leadsWildcard;
    private final boolean multiLevel;

    private MqttTopicFilter(String[] levels, boolean leadsWildcard, boolean multiLevel) {
        this.levels = levels;
        this.leadsWildcard = leadsWildcard;
        this.multiLevel = multiLevel;
    }

    static MqttTopicFilter of(String filter) {
        if (filter == null) {
            return NEVER;
        }
        String bare = stripShare(filter);
        if (bare == null || bare.isEmpty()) {
            return NEVER;
        }
        String[] levels = split(bare);
        boolean multiLevel = MULTI_LEVEL.equals(levels[levels.length - 1]);
        for (int i = 0; i < levels.length - 1; i++) {
            // '#' must be the last level of a filter (MQTT 3.1.1 4.7.1.2); a filter that breaks this can never match
            if (MULTI_LEVEL.equals(levels[i])) {
                return NEVER;
            }
        }
        boolean leadsWildcard = SINGLE_LEVEL.equals(levels[0]) || MULTI_LEVEL.equals(levels[0]);
        if (multiLevel) {
            levels = Arrays.copyOf(levels, levels.length - 1);
        }
        return new MqttTopicFilter(levels, leadsWildcard, multiLevel);
    }

    /**
     * Splits a topic or filter into levels, keeping trailing empty ones. The delivery path splits an incoming topic
     * once and passes the result to every filter it tests.
     */
    static String[] split(String topic) {
        return topic.split(LEVEL_SEPARATOR_STR, -1);
    }

    /**
     * Whether {@code topic} matches this filter. {@code topicLevels} must be {@code split(topic)} of that same
     * {@code topic} - the delivery path splits an incoming topic once and reuses the result across every filter it
     * tests against it. Neither argument may be null.
     */
    boolean matches(String topic, String[] topicLevels) {
        if (levels == null) {
            return false;
        }
        if (leadsWildcard && !topic.isEmpty() && topic.charAt(0) == DOLLAR) {
            return false;
        }
        int i = 0;
        for (; i < levels.length; i++) {
            if (i >= topicLevels.length) {
                return false;
            }
            String level = levels[i];
            if (!SINGLE_LEVEL.equals(level) && !level.equals(topicLevels[i])) {
                return false;
            }
        }
        // a terminal '#' matches the level it replaced and every level below it, including none at all
        return multiLevel || i == topicLevels.length;
    }

    /**
     * The filter past a {@code $share/<group>/} prefix, the filter itself when there is none, or null for a shared
     * subscription with an empty group or nothing after it - which can never match anything.
     */
    private static String stripShare(String filter) {
        if (!filter.startsWith(SHARE_PREFIX)) {
            return filter;
        }
        int groupEnd = filter.indexOf(LEVEL_SEPARATOR, SHARE_PREFIX.length());
        if (groupEnd <= SHARE_PREFIX.length() || groupEnd == filter.length() - 1) {
            return null;
        }
        return filter.substring(groupEnd + 1);
    }
}
