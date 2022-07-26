/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.exception.RetainMsgTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
@EnableScheduling
@ContextConfiguration(classes = RetainedMsgServiceImpl.class)
@TestPropertySource(properties = {
        "mqtt.retain-msg-trie.clear-nodes-cron=* * * * * *",
        "mqtt.retain-msg-trie.clear-nodes-zone=UTC"
})
public class RetainedMsgServiceImplTest {

    @MockBean
    RetainMsgTrie<RetainedMsg> retainMsgTrie;
    @MockBean
    StatsManager statsManager;

    @SpyBean
    RetainedMsgServiceImpl retainedMsgService;

    @Test
    public void whenWaitThreeSeconds_thenScheduledIsCalledAtLeastOneTime() throws RetainMsgTrieClearException {
        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(retainedMsgService, atLeast(1)).scheduleEmptyNodeClear());

        verify(retainMsgTrie, atLeast(1)).clearEmptyNodes();
    }

}