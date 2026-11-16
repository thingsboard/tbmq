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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.exception.PublishLimitExceededException;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgQueuePublisher;

/**
 * The entry of the publish pipeline. A publish takes its share of the {@link PipelineBudget} and goes to the dispatch
 * stage; its callback completes after the fan-out, so the PUBACK or PUBREC goes out only once every copy was handed
 * to Netty or written to the store.
 * <p>
 * When the pipeline is full, the publish fails with {@link PublishLimitExceededException}, whatever its QoS: the
 * caller decides what a refusal means for it, and counts the drop. It never blocks the caller.
 * <p>
 * A retained publish is never refused here. Its caller checks {@link #hasCapacity} first and then stores its retained
 * msg, which a refusal could no longer undo. Only the publishes that pass that check at the same moment can take the
 * pipeline past its limits, and they still count toward them.
 */
@Service
@StandaloneComponent
@RequiredArgsConstructor
public class StandalonePublishMsgQueuePublisher implements PublishMsgQueuePublisher {

    private final PipelineBudget budget;
    private final StandalonePublishMsgQueue queue;

    @Override
    public void sendMsg(TbProtoQueueMsg<PublishMsgProto> msgProto, TbQueueCallback callback) {
        PublishMsgProto publishMsgProto = msgProto.getValue();
        int bytes = publishMsgProto.getPayload().size();
        if (publishMsgProto.getRetain()) {
            budget.acquire(bytes);
        } else if (!budget.tryAcquire(bytes)) {
            callback.onFailure(new PublishLimitExceededException("pipeline is full"));
            return;
        }
        queue.submit(msgProto, new CompleteOncePublishCallback(budget, bytes, callback));
    }

    @Override
    public boolean hasCapacity(int payloadBytes) {
        return budget.hasRoom(payloadBytes);
    }

}
