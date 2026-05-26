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

public final class SparkplugTopicUtil {

    public static final String CERTIFICATE_TOPIC_PREFIX = "$sparkplug/certificates/";

    private static final String SP_BV_1_0_NAMESPACE = "spBv1.0";
    private static final String SP_BV_1_0_PREFIX = SP_BV_1_0_NAMESPACE + "/";
    private static final String NBIRTH = "NBIRTH";
    private static final String DBIRTH = "DBIRTH";

    private SparkplugTopicUtil() {
    }

    /**
     * If the given topic is a Sparkplug B v1.0 NBIRTH or DBIRTH topic, returns the
     * corresponding {@code $sparkplug/certificates/...} topic that a Sparkplug Aware
     * MQTT Server must republish the message on with retain=true. Otherwise returns null.
     *
     * <p>Recognizes only the {@code spBv1.0} namespace, and only the NBIRTH/DBIRTH
     * message types. NDATA/NCMD/NDEATH/DDATA/DCMD/DDEATH/STATE return null — they are
     * not retained-republished per Sparkplug 3.0 §10.1.4.
     */
    public static String toCertificateTopic(String topic) {
        // Fast-path: cheap prefix check short-circuits the 99% of publishes that aren't
        // Sparkplug, avoiding String.split allocation on the publish hot path.
        if (topic == null || !topic.startsWith(SP_BV_1_0_PREFIX)) {
            return null;
        }
        if (!isNbirth(topic) && !isDbirth(topic)) {
            return null;
        }
        return CERTIFICATE_TOPIC_PREFIX + topic;
    }

    private static boolean isNbirth(String topic) {
        return matchesBirth(topic, NBIRTH, 4);
    }

    private static boolean isDbirth(String topic) {
        return matchesBirth(topic, DBIRTH, 5);
    }

    private static boolean matchesBirth(String topic, String messageType, int expectedSegments) {
        if (topic == null || topic.isEmpty()) {
            return false;
        }
        String[] parts = topic.split("/", -1);
        if (parts.length != expectedSegments) {
            return false;
        }
        if (!SP_BV_1_0_NAMESPACE.equals(parts[0])) {
            return false;
        }
        if (!messageType.equals(parts[2])) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.indexOf('+') >= 0 || part.indexOf('#') >= 0) {
                return false;
            }
        }
        return true;
    }
}
