package org.thingsboard.mqtt.broker.service.mqtt.retain;

import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;

public interface RetainedMsgPageService {

    PageData<RetainedMsgDto> getRetainedMessages(PageLink pageLink);

}
