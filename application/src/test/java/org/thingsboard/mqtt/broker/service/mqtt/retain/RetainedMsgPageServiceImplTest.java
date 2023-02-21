package org.thingsboard.mqtt.broker.service.mqtt.retain;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class RetainedMsgPageServiceImplTest {

    RetainedMsgListenerService retainedMsgListenerService;
    RetainedMsgPageServiceImpl retainedMsgPageService;

    @Before
    public void setUp() {
        retainedMsgListenerService = mock(RetainedMsgListenerService.class);
        retainedMsgPageService = spy(new RetainedMsgPageServiceImpl(retainedMsgListenerService));

        List<RetainedMsg> retainedMsgs = getAllRetainedMessages();
        doReturn(retainedMsgs).when(retainedMsgListenerService).getRetainedMessages();
    }

    private List<RetainedMsg> getAllRetainedMessages() {
        return List.of(
                getRetainedMsg("topic/test1"),
                getRetainedMsg("topic/test2"),
                getRetainedMsg("topic/test3"),
                getRetainedMsg("my/topic"),
                getRetainedMsg("home/temp")
        );
    }

    @Test
    public void testGetRetainedMessagesWithPageLink() {
        PageData<RetainedMsgDto> retainedMessages = retainedMsgPageService.getRetainedMessages(
                new PageLink(3, 0));
        List<RetainedMsgDto> data = retainedMessages.getData();

        assertEquals(3, data.size());
        assertEquals(5, retainedMessages.getTotalElements());
        assertTrue(retainedMessages.hasNext());
    }

    @Test
    public void testGetRetainedMessagesWithPageLinkAndTextSearch() {
        PageData<RetainedMsgDto> retainedMessages = retainedMsgPageService.getRetainedMessages(
                new PageLink(10, 0, "topic"));
        List<RetainedMsgDto> data = retainedMessages.getData();

        assertEquals(4, data.size());
        assertEquals(4, retainedMessages.getTotalElements());
        assertFalse(retainedMessages.hasNext());
    }

    @Test
    public void testGetRetainedMessagesWithPageLinkAndSorting() {
        PageData<RetainedMsgDto> retainedMessages = retainedMsgPageService.getRetainedMessages(
                new PageLink(10, 0, null, new SortOrder("topic", SortOrder.Direction.ASC)));
        List<RetainedMsgDto> data = retainedMessages.getData();

        assertEquals(5, data.size());
        assertEquals(5, retainedMessages.getTotalElements());
        assertFalse(retainedMessages.hasNext());

        assertEquals("home/temp", data.get(0).getTopic());
        assertEquals("topic/test3", data.get(4).getTopic());
    }

    private static RetainedMsg getRetainedMsg(String topic) {
        return new RetainedMsg(topic, "payload".getBytes(StandardCharsets.UTF_8), 1);
    }
}