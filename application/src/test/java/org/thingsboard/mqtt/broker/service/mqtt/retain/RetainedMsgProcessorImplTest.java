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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class RetainedMsgProcessorImplTest {

    RetainedMsgListenerService retainedMsgListenerService;
    RetainedMsgProcessorImpl retainedMsgProcessor;

    @Before
    public void setUp() {
        retainedMsgListenerService = mock(RetainedMsgListenerService.class);
        retainedMsgProcessor = spy(new RetainedMsgProcessorImpl(retainedMsgListenerService));
    }

    @Test
    public void testProcessRetainMsg() {
        PublishMsg publishMsg = getPublishMsg();

        retainedMsgProcessor.process(publishMsg);

        verify(retainedMsgListenerService, times(1)).cacheRetainedMsgAndPersist(eq("test"), any(), any());
    }

    @Test
    public void testProcessEmptyRetainMsg() {
        PublishMsg publishMsg = emptyPublishMsg();

        retainedMsgProcessor.process(publishMsg);

        verify(retainedMsgListenerService, times(1)).clearRetainedMsgAndPersist(eq("test"), any());
    }

    @Test
    public void givenAStoreThatFailsBeforeTheCallReturns_whenProcess_thenTheStoreExceptionIsThrown() {
        StoreException failure = new StoreException("disk");
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(2).onFailure(failure);
            return null;
        }).when(retainedMsgListenerService).cacheRetainedMsgAndPersist(eq("test"), any(), any());
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(1).onFailure(failure);
            return null;
        }).when(retainedMsgListenerService).clearRetainedMsgAndPersist(eq("test"), any());

        // the publish must fail: the caller counts it and answers it as failed
        assertThatThrownBy(() -> retainedMsgProcessor.process(getPublishMsg())).isSameAs(failure);
        assertThatThrownBy(() -> retainedMsgProcessor.process(emptyPublishMsg())).isSameAs(failure);
    }

    @Test
    public void givenAFailureThatIsNotFromTheStore_whenProcess_thenItIsOnlyLogged() {
        // what the cluster producer reports: never a StoreException, and normally after the call returned
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(2).onFailure(new RuntimeException("kafka"));
            return null;
        }).when(retainedMsgListenerService).cacheRetainedMsgAndPersist(eq("test"), any(), any());

        PublishMsg publishMsg = getPublishMsg();

        assertThat(retainedMsgProcessor.process(publishMsg)).isSameAs(publishMsg);
    }

    private PublishMsg emptyPublishMsg() {
        return getMsg(new byte[0]);
    }

    private PublishMsg getPublishMsg() {
        return getMsg("data".getBytes());
    }

    private PublishMsg getMsg(byte[] payload) {
        return new PublishMsg(1, "test", payload, 2, true, false);
    }
}