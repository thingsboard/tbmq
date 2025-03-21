/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        verify(retainedMsgListenerService, times(1)).cacheRetainedMsgAndPersist(eq("test"), any());
    }

    @Test
    public void testProcessEmptyRetainMsg() {
        PublishMsg publishMsg = emptyPublishMsg();

        retainedMsgProcessor.process(publishMsg);

        verify(retainedMsgListenerService, times(1)).clearRetainedMsgAndPersist(eq("test"));
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