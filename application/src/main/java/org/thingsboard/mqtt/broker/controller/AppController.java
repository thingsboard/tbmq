/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.config.BrokerHomePageConfig;
import org.thingsboard.mqtt.broker.dto.HomePageConfigDto;
import org.thingsboard.mqtt.broker.dto.MqttListenerName;
import org.thingsboard.mqtt.broker.service.entity.app.TbAppService;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app")
@Slf4j
public class AppController extends BaseController {

    private final BrokerHomePageConfig brokerHomePageConfig;
    private final SystemInfoService systemInfoService;
    private final TbAppService tbAppService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/config")
    public HomePageConfigDto getBrokerConfig() throws ThingsboardException {
        return checkNotNull(brokerHomePageConfig.getConfig());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/listener/port")
    public int getListenerPort(@RequestParam String listenerName) throws ThingsboardException {
        checkParameter("listenerName", listenerName);
        MqttListenerName mqttListenerName = MqttListenerName.valueOf(listenerName.toUpperCase());
        return brokerHomePageConfig.getListenerPort(mqttListenerName);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/brokers")
    public List<String> getBrokerServiceIds() throws ThingsboardException {
        return checkNotNull(systemInfoService.getTbmqServiceIds());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/service/info")
    public DeferredResult<ResponseEntity> getServiceInfos() throws ThingsboardException {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        DonAsynchron.withCallback(systemInfoService.getServiceInfos(),
                serviceInfos -> result.setResult(ResponseEntity.ok(serviceInfos)),
                throwable -> handleError(throwable, result, HttpStatus.INTERNAL_SERVER_ERROR));

        return result;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/service/info")
    public void removeServiceInfo(@RequestParam String serviceId) throws ThingsboardException {
        checkParameter("serviceId", serviceId);
        tbAppService.deleteServiceInfo(serviceId, getCurrentUser());
    }
}
