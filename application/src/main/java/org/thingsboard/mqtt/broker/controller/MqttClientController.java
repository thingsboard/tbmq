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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.MqttClient;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;

@RestController
@RequestMapping("/api")
public class MqttClientController extends BaseController {

    @Autowired
    private MqttClientCredentialsService mqttClientCredentialsService;

    @Autowired
    private MqttClientService mqttClientService;

    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @RequestMapping(value = "/mqtt/client/credentials", method = RequestMethod.POST)
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
    @RequestMapping(value = "/mqtt/client", method = RequestMethod.POST)
    @ResponseBody
    public MqttClient saveMqttClient(@RequestBody MqttClient mqttClient) throws ThingsboardException {
        checkNotNull(mqttClient);
        try {
            return checkNotNull(
                    mqttClientService.saveMqttClient(mqttClient)
            );
        } catch (Exception e) {
            throw handleException(e);
        }
    }



}
