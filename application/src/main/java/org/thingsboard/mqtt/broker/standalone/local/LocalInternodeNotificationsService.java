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
package org.thingsboard.mqtt.broker.standalone.local;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsApplier;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

/**
 * There are no other nodes to notify: the notification is applied in-process. As in the cluster consumer, a failure
 * to apply it is only logged, so it never reaches the caller that already saved the change.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class LocalInternodeNotificationsService implements InternodeNotificationsService {

    private final InternodeNotificationsApplier applier;
    private final ServiceInfoProvider serviceInfoProvider;

    @Override
    public void broadcast(InternodeNotificationProto notificationProto) {
        try {
            applier.apply(serviceInfoProvider.getServiceId(), notificationProto);
        } catch (Exception e) {
            log.warn("Failed to process internode notification [{}].", notificationProto, e);
        }
    }

}
