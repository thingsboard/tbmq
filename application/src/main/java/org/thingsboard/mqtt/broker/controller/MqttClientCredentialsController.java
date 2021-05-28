/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;

@RestController
@RequestMapping("/api/mqtt/client/credentials")
public class MqttClientCredentialsController extends BaseController {

    @Autowired
    private MqttClientCredentialsService mqttClientCredentialsService;

    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public MqttClientCredentials saveMqttClientCredentials(@RequestBody MqttClientCredentials mqttClientCredentials) throws ThingsboardException {
        checkNotNull(mqttClientCredentials);
        try {
            return checkNotNull(
                mqttClientCredentialsService.saveCredentials(mqttClientCredentials)
            );
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<MqttClientCredentials> getMqttClientCredentials(@RequestParam int pageSize, @RequestParam int page) throws ThingsboardException {
        try {
            PageLink pageLink = new PageLink(pageSize, page);
            return checkNotNull(mqttClientCredentialsService.getCredentials(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "/{credentialsId}", method = RequestMethod.DELETE)
    public void deleteCredentials(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        try {
            mqttClientCredentialsService.deleteCredentials(toUUID(strCredentialsId));
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
