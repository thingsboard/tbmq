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
package org.thingsboard.mqtt.broker.service.entity.retainedmsg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.exception.RetainMsgTrieClearException;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbRetainedMsgService extends AbstractTbEntityService implements TbRetainedMsgService {

    private final RetainedMsgService retainedMsgService;
    private final RetainedMsgListenerService retainedMsgListenerService;

    @Override
    public void clearEmptyNodes(User currentUser) {
        try {
            retainedMsgService.clearEmptyTopicNodes();
        } catch (RetainMsgTrieClearException e) {
            throw new ThingsboardRuntimeException(e.getMessage(), ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public void delete(String topicName, User currentUser) {
        retainedMsgListenerService.clearRetainedMsgAndPersist(topicName);
    }

}
