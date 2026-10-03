/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.service.topictree;

import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
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
}
