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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaBroker;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;
import org.thingsboard.mqtt.broker.config.BrokerHomePageConfig;
import org.thingsboard.mqtt.broker.dto.HomePageConfigDto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventProcessor;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
public class AppController extends BaseController {

    private final TbActorSystem tbActorSystem;
    private final ApplicationRemovedEventProcessor applicationRemovedEventProcessor;
    private final TbQueueAdmin tbQueueAdmin;
    private final BrokerHomePageConfig brokerHomePageConfig;

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
    public PageData<KafkaBroker> getKafkaClusterInfo() throws ThingsboardException {
        try {
            return checkNotNull(tbQueueAdmin.getClusterInfo());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/kafka-topics", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<KafkaTopic> getKafkaTopics(@RequestParam int pageSize,
                                               @RequestParam int page,
                                               @RequestParam(required = false) String textSearch,
                                               @RequestParam(required = false) String sortProperty,
                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(tbQueueAdmin.getTopics(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/consumer-groups", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<KafkaConsumerGroup> getKafkaConsumerGroups(@RequestParam int pageSize,
                                                               @RequestParam int page,
                                                               @RequestParam(required = false) String textSearch,
                                                               @RequestParam(required = false) String sortProperty,
                                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(tbQueueAdmin.getConsumerGroups(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/config", method = RequestMethod.GET)
    @ResponseBody
    public HomePageConfigDto getBrokerConfig() throws ThingsboardException {
        try {
            return brokerHomePageConfig.getConfig();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    // TODO: 10.04.23 remove this!
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/alarm", method = RequestMethod.GET)
    @ResponseBody
    public void alarm() throws ThingsboardException {
        try {
            tbQueueAdmin.deleteAppTopics();
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
