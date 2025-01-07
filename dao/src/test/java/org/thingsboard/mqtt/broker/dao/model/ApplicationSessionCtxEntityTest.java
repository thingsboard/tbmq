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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.ApplicationMsgInfo;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationSessionCtxEntityTest {

    @Test
    public void testConstructorAndToData() {
        ObjectNode publish1 = JacksonUtil.newObjectNode();
        publish1.put("offset", 2L);
        publish1.put("packetId", 3);

        ObjectNode publish2 = JacksonUtil.newObjectNode();
        publish2.put("offset", 3L);
        publish2.put("packetId", 4);

        ArrayNode publishMsgs = JacksonUtil.newArrayNode();
        publishMsgs.add(publish1);
        publishMsgs.add(publish2);

        ObjectNode pubrel1 = JacksonUtil.newObjectNode();
        pubrel1.put("offset", 0L);
        pubrel1.put("packetId", 1);

        ObjectNode pubrel2 = JacksonUtil.newObjectNode();
        pubrel2.put("offset", 1L);
        pubrel2.put("packetId", 2);

        ArrayNode pubrelMsgs = JacksonUtil.newArrayNode();
        pubrelMsgs.add(pubrel1);
        pubrelMsgs.add(pubrel2);

        List<ApplicationMsgInfo> publishMsgInfos = Arrays.asList(
                new ApplicationMsgInfo(2L, 3),
                new ApplicationMsgInfo(3L, 4)
        );
        List<ApplicationMsgInfo> pubRelMsgInfos = Arrays.asList(
                new ApplicationMsgInfo(0L, 1),
                new ApplicationMsgInfo(1L, 2)
        );
        long ts = System.currentTimeMillis();
        String clientId = "client";

        ApplicationSessionCtx sessionCtx = ApplicationSessionCtx.builder()
                .clientId(clientId)
                .lastUpdatedTime(ts)
                .publishMsgInfos(publishMsgInfos)
                .pubRelMsgInfos(pubRelMsgInfos)
                .build();

        ApplicationSessionCtxEntity entity = new ApplicationSessionCtxEntity(sessionCtx);
        ApplicationSessionCtx reconstructedCtx = entity.toData();

        assertThat(entity.getClientId()).isEqualTo(clientId);
        assertThat(entity.getLastUpdatedTime()).isEqualTo(ts);
        assertThat(entity.getPublishMsgInfos().toPrettyString()).isEqualTo(publishMsgs.toPrettyString());
        assertThat(entity.getPubRelMsgInfos().toPrettyString()).isEqualTo(pubrelMsgs.toPrettyString());

        assertThat(reconstructedCtx.getClientId()).isEqualTo(clientId);
        assertThat(reconstructedCtx.getLastUpdatedTime()).isEqualTo(ts);
        assertThat(reconstructedCtx.getPublishMsgInfos()).isEqualTo(publishMsgInfos);
        assertThat(reconstructedCtx.getPubRelMsgInfos()).isEqualTo(pubRelMsgInfos);
    }
}
