/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.client;


import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActor;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.service.ContextBasedCreator;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;

public class ClientActorCreator extends ContextBasedCreator {

    private final String clientId;
    private final boolean isClientIdGenerated;

    public ClientActorCreator(ActorSystemContext context, String clientId, boolean isClientIdGenerated) {
        super(context);
        this.clientId = clientId;
        this.isClientIdGenerated = isClientIdGenerated;
    }

    @Override
    public TbActorId createActorId() {
        return new TbTypeActorId(ActorType.CLIENT, clientId);
    }

    @Override
    public TbActor createActor() {
        return new ClientActor(context, clientId, isClientIdGenerated);
    }

}
