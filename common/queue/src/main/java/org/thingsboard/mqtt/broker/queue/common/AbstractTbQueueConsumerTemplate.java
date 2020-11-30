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
package org.thingsboard.mqtt.broker.queue.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractTbQueueConsumerTemplate<R, T extends TbQueueMsg> implements TbQueueConsumer<T> {

    private volatile boolean subscribed;
    protected volatile boolean stopped = false;
    protected volatile Set<TopicInfo> topics;
    protected final Lock consumerLock = new ReentrantLock();

    @Getter
    private final String topic;

    public AbstractTbQueueConsumerTemplate(String topic) {
        this.topic = topic;
    }

    @Override
    public void subscribe() {
        consumerLock.lock();
        try {
            topics = Collections.singleton(new TopicInfo(topic));
            subscribed = false;
        } finally {
            consumerLock.unlock();
        }
    }

    @Override
    public void subscribe(Set<TopicInfo> topics) {
        consumerLock.lock();
        try {
            this.topics = topics;
            subscribed = false;
        } finally {
            consumerLock.unlock();
        }
    }

    @Override
    public List<T> poll(long durationInMillis) {
        if (!subscribed && topics == null) {
            try {
                Thread.sleep(durationInMillis);
            } catch (InterruptedException e) {
                log.debug("Failed to await subscription", e);
            }
        } else {
            long pollStartTs = System.currentTimeMillis();
            consumerLock.lock();
            try {
                if (!subscribed) {
                    List<String> topicNames = topics.stream().map(TopicInfo::getTopic).collect(Collectors.toList());
                    doSubscribe(topicNames);
                    subscribed = true;
                }

                List<R> records;
                if (topics.isEmpty()) {
                    records = Collections.emptyList();
                } else {
                    records = doPoll(durationInMillis);
                }
                if (!records.isEmpty()) {
                    List<T> result = new ArrayList<>(records.size());
                    records.forEach(record -> {
                        try {
                            if (record != null) {
                                result.add(decode(record));
                            }
                        } catch (IOException e) {
                            log.error("Failed decode record: [{}]", record);
                            throw new RuntimeException("Failed to decode record: ", e);
                        }
                    });
                    return result;
                } else {
                    long pollDuration = System.currentTimeMillis() - pollStartTs;
                    if (pollDuration < durationInMillis) {
                        try {
                            Thread.sleep(durationInMillis - pollDuration);
                        } catch (InterruptedException e) {
                            if (!stopped) {
                                log.error("Failed to wait.", e);
                            }
                        }
                    }
                }
            } finally {
                consumerLock.unlock();
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void commit() {
        consumerLock.lock();
        try {
            doCommit();
        } finally {
            consumerLock.unlock();
        }
    }

    @Override
    public void unsubscribe() {
        stopped = true;
        consumerLock.lock();
        try {
            doUnsubscribe();
        } finally {
            consumerLock.unlock();
        }
    }

    abstract protected List<R> doPoll(long durationInMillis);

    abstract protected T decode(R record) throws IOException;

    abstract protected void doSubscribe(List<String> topicNames);

    abstract protected void doCommit();

    abstract protected void doUnsubscribe();

}
