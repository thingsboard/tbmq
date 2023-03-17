/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.queue.ClusterInfo;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventProcessor;

import java.util.Collection;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
public class AppController extends BaseController {

    private final TbActorSystem tbActorSystem;
    private final ApplicationRemovedEventProcessor applicationRemovedEventProcessor;
    private final TbQueueAdmin tbQueueAdmin;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @ApiOperation(value = "Get all running actors", hidden = true)
    @RequestMapping(value = "/active-actors", method = RequestMethod.GET)
    @ResponseBody
    public Collection<TbActorId> getAllActorIds() throws ThingsboardException {
        try {
            return tbActorSystem.getAllActorIds();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/remove-topics", method = RequestMethod.DELETE)
    @ResponseBody
    public void removeApplicationTopics() throws ThingsboardException {
        try {
            applicationRemovedEventProcessor.processEvents();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/cluster-info", method = RequestMethod.GET)
    @ResponseBody
    public ClusterInfo getKafkaClusterInfo() throws ThingsboardException {
        try {
            return tbQueueAdmin.getClusterInfo();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/kafka-topics", method = RequestMethod.GET)
    @ResponseBody
    public List<KafkaTopic> getKafkaTopics() throws ThingsboardException {
        try {
            return tbQueueAdmin.getTopics();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/consumer-groups", method = RequestMethod.GET)
    @ResponseBody
    public List<KafkaConsumerGroup> getKafkaConsumerGroups() throws ThingsboardException {
        try {
            return tbQueueAdmin.getConsumerGroups();
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
