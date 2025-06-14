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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaBroker;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.config.BrokerHomePageConfig;
import org.thingsboard.mqtt.broker.config.annotations.ApiOperation;
import org.thingsboard.mqtt.broker.dto.HomePageConfigDto;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventProcessor;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
@Slf4j
public class AppController extends BaseController {

    private final ApplicationRemovedEventProcessor applicationRemovedEventProcessor;
    private final BrokerHomePageConfig brokerHomePageConfig;
    private final SystemInfoService systemInfoService;

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
    @RequestMapping(value = "/consumer-group", method = RequestMethod.DELETE)
    @ApiOperation(value = "Delete Kafka Consumer Group", hidden = true)
    public void deleteKafkaConsumerGroup(@RequestParam String groupId) throws ThingsboardException {
        try {
            tbQueueAdmin.deleteConsumerGroup(groupId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/config", method = RequestMethod.GET)
    @ResponseBody
    public HomePageConfigDto getBrokerConfig() throws ThingsboardException {
        try {
            return checkNotNull(brokerHomePageConfig.getConfig());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/brokers", method = RequestMethod.GET)
    @ResponseBody
    public List<String> getBrokerServiceIds() throws ThingsboardException {
        try {
            return checkNotNull(systemInfoService.getTbmqServiceIds());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/service/info", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getServiceInfos() throws ThingsboardException {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        DonAsynchron.withCallback(systemInfoService.getServiceInfos(),
                serviceInfos -> result.setResult(ResponseEntity.ok(serviceInfos)),
                throwable -> handleError(throwable, result, HttpStatus.INTERNAL_SERVER_ERROR));

        return result;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/service/info", method = RequestMethod.DELETE)
    @ResponseBody
    public void removeServiceInfo(@RequestParam String serviceId) throws ThingsboardException {
        systemInfoService.removeServiceInfo(serviceId);
    }
}
