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
package org.thingsboard.mqtt.broker.service.mqtt.validation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Data
@Service
public class PublishMsgValidationServiceImpl implements PublishMsgValidationService {

    private final TopicValidationService topicValidationService;
    private final AuthorizationRuleService authorizationRuleService;

    @Override
    public boolean validatePubMsg(ClientSessionCtx ctx, String clientId, PublishMsg publishMsg) {
        return doValidatePubMsg(ctx, clientId, publishMsg);
    }

    @Override
    public boolean validatePubMsg(ClientSessionCtx ctx, PublishMsg publishMsg) {
        return doValidatePubMsg(ctx, ctx.getClientId(), publishMsg);
    }

    private boolean doValidatePubMsg(ClientSessionCtx ctx, String clientId, PublishMsg publishMsg) {
        topicValidationService.validateTopic(publishMsg.getTopicName());
        return validateClientAccess(ctx, clientId, publishMsg.getTopicName());
    }

    boolean validateClientAccess(ClientSessionCtx ctx, String clientId, String topic) {
        boolean isClientAuthorized = authorizationRuleService.isPubAuthorized(clientId, topic, ctx.getAuthRulePatterns());
        if (!isClientAuthorized) {
            log.warn("[{}][{}][{}] Client is not authorized to publish to the topic {}",
                    clientId, ctx.getSessionId(), ctx.getAuthRulePatterns(), topic);
        }
        return isClientAuthorized;
    }
}
