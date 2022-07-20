/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.thingsboard.mqtt.broker.actors.AbstractTbActor;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.ProcessFailureStrategy;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;

@Slf4j
public abstract class ContextAwareActor extends AbstractTbActor {

    protected final ActorSystemContext systemContext;
    private final StopWatch stopWatch;

    public ContextAwareActor(ActorSystemContext systemContext) {
        super();
        this.systemContext = systemContext;
        this.stopWatch = new StopWatch();
    }

    @Override
    public boolean process(TbActorMsg msg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing msg: {}", getActorId(), msg.getMsgType());
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] Processing msg: {}", getActorId(), msg);
        }

        stopWatch.start();
        try {
            if (!doProcess(msg)) {
                log.warn("[{}] Unprocessed message: {}!", getActorId(), msg);
            }
        } finally {
            stopWatch.stop();
            systemContext.getActorProcessingMetricService().logMsgProcessingTime(msg.getMsgType(), stopWatch.getTime());
            stopWatch.reset();
        }
        return false;
    }

    protected abstract boolean doProcess(TbActorMsg msg);

    @Override
    public ProcessFailureStrategy onProcessFailure(Throwable t) {
        log.debug("[{}] Processing failure: ", getActorId(), t);
        return doProcessFailure(t);
    }

    private TbActorId getActorId() {
        return getActorRef() != null ? getActorRef().getActorId() : null;
    }

    private ProcessFailureStrategy doProcessFailure(Throwable t) {
        if (t instanceof Error) {
            return ProcessFailureStrategy.stop();
        } else {
            return ProcessFailureStrategy.resume();
        }
    }
}
