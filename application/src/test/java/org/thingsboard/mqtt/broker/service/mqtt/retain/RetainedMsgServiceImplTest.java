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