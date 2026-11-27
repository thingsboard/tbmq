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
package org.thingsboard.mqtt.broker.service.topictree;

import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class TopicTreeFixtures {

    public record Data(Map<String, Set<TopicSubscription>> subs, List<String> retained, Map<String, ClientSessionInfo> sessions) {
    }

    private TopicTreeFixtures() {
    }

    static ClientTopicSubscription sub(String filter, int qos) {
        return new ClientTopicSubscription(filter, qos);
    }

    static ClientTopicSubscription shared(String filter, String group) {
        return new ClientTopicSubscription(filter, 1, group);
    }

    static ClientSessionInfo session(String clientId, boolean connected, ClientType type) {
        return ClientSessionInfo.builder().clientId(clientId).connected(connected).type(type).build();
    }

    // A small hand-made tree shared by the unit tests: c1 (online, device) and the shared group g1 (c2 offline, c3
    // online) under sensors, an integration on sensors/#, edge-case filters with empty segments, and retained
    // messages on two sensor topics plus the subscription-less "other".
    public static TopicTreeSnapshot fixture() {
        Map<String, Set<TopicSubscription>> subs = new HashMap<>();
        subs.put("c1", Set.of(sub("sensors/+/temperature", 1), sub("sensors/room1/#", 0)));
        subs.put("c2", Set.of(shared("sensors/+/temperature", "g1")));
        subs.put("c3", Set.of(shared("sensors/+/temperature", "g1"), sub("/leading", 0), sub("a//b", 0)));
        subs.put("int-1", Set.of(new IntegrationTopicSubscription("sensors/#")));
        Map<String, ClientSessionInfo> sessions = Map.of(
                "c1", session("c1", true, ClientType.DEVICE),
                "c2", session("c2", false, ClientType.APPLICATION),
                "c3", session("c3", true, null));
        return TopicTreeSnapshotBuilder.build(subs, List.of("sensors/room1/temperature", "sensors/room2/temperature", "other"), sessions, 1L);
    }

    public static Data synthetic(int devices, int fleetFilters, int retained) {
        Random rnd = new Random(42);
        Map<String, Set<TopicSubscription>> subs = new HashMap<>();
        Map<String, ClientSessionInfo> sessions = new HashMap<>();
        for (int i = 0; i < devices; i++) {
            String id = "dev-" + i;
            Set<TopicSubscription> s = new HashSet<>();
            s.add(new ClientTopicSubscription("devices/" + id + "/cmd", 1));
            s.add(new ClientTopicSubscription("devices/" + id + "/cfg", 1));
            subs.put(id, s);
            sessions.put(id, ClientSessionInfo.builder().clientId(id).connected(rnd.nextInt(10) < 8).build());
        }
        String[] regions = {"eu", "us", "ap", "sa"};
        for (int f = 0; f < fleetFilters; f++) {
            String filter = "fleet/" + regions[f % 4] + "/site" + (f % 37) + "/line" + (f % 11) + "/+/telemetry" + f;
            int subscribers = 1 + rnd.nextInt(50);
            for (int k = 0; k < subscribers; k++) {
                String id = "app-" + rnd.nextInt(devices / 10 + 1);
                subs.computeIfAbsent(id, x -> new HashSet<>()).add(new ClientTopicSubscription(filter, rnd.nextInt(3)));
                sessions.putIfAbsent(id, ClientSessionInfo.builder().clientId(id).connected(true).build());
            }
        }
        String[] wild = {"#", "sensors/#", "sensors/+/temperature", "devices/+/cmd", "fleet/+/+/#"};
        for (int w = 0; w < 200; w++) {
            String id = "wild-" + w;
            subs.computeIfAbsent(id, x -> new HashSet<>()).add(new ClientTopicSubscription(wild[w % wild.length], 0));
            if (w % 4 == 0) {
                subs.computeIfAbsent("wild-shared-" + w, x -> new HashSet<>()).add(new ClientTopicSubscription(wild[w % wild.length], 1, "group" + (w % 50)));
            }
        }
        for (int n = 0; n < 10; n++) {
            subs.put("int-" + n, Set.of(new IntegrationTopicSubscription("fleet/" + regions[n % 4] + "/#")));
        }
        List<String> retainedTopics = new ArrayList<>(retained);
        for (int r = 0; r < retained; r++) {
            retainedTopics.add("devices/dev-" + (r % Math.max(devices, 1)) + "/state");
        }
        return new Data(subs, retainedTopics, sessions);
    }

    // Spec heap budget shape: about 1M subscriptions on about 100k nodes. Extra application clients fan out over the
    // existing fleet filters, so subscriptions grow without adding nodes.
    public static Data fanOut(int devices, int fleetFilters, int retained, int targetSubscriptions) {
        Data base = synthetic(devices, fleetFilters, retained);
        String[] regions = {"eu", "us", "ap", "sa"};
        int existing = base.subs().values().stream().mapToInt(Set::size).sum();
        int perClient = 50;
        Random rnd = new Random(7);
        for (int c = 0; existing < targetSubscriptions; c++) {
            String id = "fan-" + c;
            Set<TopicSubscription> s = new HashSet<>();
            for (int k = 0; k < perClient; k++) {
                int f = rnd.nextInt(fleetFilters);
                s.add(new ClientTopicSubscription("fleet/" + regions[f % 4] + "/site" + (f % 37) + "/line" + (f % 11) + "/+/telemetry" + f, 1));
            }
            existing += s.size();
            base.subs().put(id, s);
            base.sessions().put(id, ClientSessionInfo.builder().clientId(id).connected(true).build());
        }
        return base;
    }
}
