/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mqtt/client/credentials")
public class MqttClientCredentialsController extends BaseController {

    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final BCryptPasswordEncoder passwordEncoder;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public MqttClientCredentials saveMqttClientCredentials(@RequestBody MqttClientCredentials mqttClientCredentials) throws ThingsboardException {
        checkNotNull(mqttClientCredentials);
        try {
            if (ClientCredentialsType.MQTT_BASIC == mqttClientCredentials.getCredentialsType()) {
                BasicMqttCredentials mqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(mqttClientCredentials, BasicMqttCredentials.class);
                mqttCredentials.setPassword(encodePasswordIfNotEmpty(mqttCredentials.getPassword()));
                mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
            }

            return checkNotNull(mqttClientCredentialsService.saveCredentials(mqttClientCredentials));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ShortMqttClientCredentials> getCredentials(@RequestParam int pageSize,
                                                               @RequestParam int page,
                                                               @RequestParam(required = false) String textSearch,
                                                               @RequestParam(required = false) String sortProperty,
                                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(mqttClientCredentialsService.getCredentials(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{credentialsId}", method = RequestMethod.GET)
    public MqttClientCredentials getCredentialsById(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        try {
            return mqttClientCredentialsService.getCredentialsById(toUUID(strCredentialsId)).orElse(null);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{credentialsId}", method = RequestMethod.DELETE)
    public void deleteCredentials(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        try {
            mqttClientCredentialsService.deleteCredentials(toUUID(strCredentialsId));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{credentialsId}", method = RequestMethod.POST)
    public void changeMqttBasicCredentialsPassword(@PathVariable("credentialsId") String strCredentialsId,
                                                   @RequestBody ChangePasswordRequest changePasswordRequest) throws ThingsboardException {
        try {
            MqttClientCredentials mqttClientCredentials = getCredentialsById(strCredentialsId);
            if (mqttClientCredentials.getCredentialsType() != ClientCredentialsType.MQTT_BASIC) {
                throw new ThingsboardException("MQTT credentials should be of 'MQTT_BASIC' type!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }

            BasicMqttCredentials basicMqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(mqttClientCredentials, BasicMqttCredentials.class);

            validatePassword(passwordEncoder, changePasswordRequest, basicMqttCredentials.getPassword());

            basicMqttCredentials.setPassword(encodePasswordIfNotEmpty(changePasswordRequest.getNewPassword()));
            mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));

            mqttClientCredentialsService.saveCredentials(mqttClientCredentials);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private String encodePasswordIfNotEmpty(String password) {
        return StringUtils.isEmpty(password) ? null : passwordEncoder.encode(password);
    }
}
