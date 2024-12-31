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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;

@Hidden
@RestController
@RequestMapping("/api")
@Slf4j
public class SystemInfoController extends BaseController {

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @PostConstruct
    public void init() {
        JsonNode info = buildInfoObject();
        log.info("System build info: {}", info);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/system/info", method = RequestMethod.GET)
    @ResponseBody
    public JsonNode getSystemVersionInfo() {
        return buildInfoObject();
    }

    private JsonNode buildInfoObject() {
        ObjectNode infoObject = JacksonUtil.newObjectNode();
        infoObject.put("newestVersion", getLatestVersionAvailable());
        if (buildProperties != null) {
            infoObject.put("version", buildProperties.getVersion());
            infoObject.put("artifact", buildProperties.getArtifact());
            infoObject.put("name", buildProperties.getName());
        } else {
            infoObject.put("version", BrokerConstants.UNKNOWN);
        }
        return infoObject;
    }

    private String getLatestVersionAvailable() {
        RetainedMsgDto retainedMsgDto = retainedMsgListenerService.getRetainedMsgForTopic(BrokerConstants.LATEST_VERSION_AVAILABLE_TOPIC_NAME);
        return retainedMsgDto == null ? BrokerConstants.UNKNOWN : retainedMsgDto.getPayload();
    }
}
