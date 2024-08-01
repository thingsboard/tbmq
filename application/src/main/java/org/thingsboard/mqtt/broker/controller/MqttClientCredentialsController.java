/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.dto.ClientCredentialsInfoDto;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MqttClientCredentialsController extends BaseController {

    private final BCryptPasswordEncoder passwordEncoder;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/client/credentials", method = RequestMethod.POST)
    @ResponseBody
    public MqttClientCredentials saveMqttClientCredentials(@RequestBody MqttClientCredentials mqttClientCredentials) throws ThingsboardException {
        checkNotNull(mqttClientCredentials);
        try {
            if (ClientCredentialsType.MQTT_BASIC == mqttClientCredentials.getCredentialsType() && mqttClientCredentials.getId() == null) {
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
    @RequestMapping(value = "/mqtt/client/credentials", params = {"pageSize", "page"}, method = RequestMethod.GET)
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
    @RequestMapping(value = "/mqtt/client/credentials/{credentialsId}", method = RequestMethod.GET)
    public MqttClientCredentials getCredentialsById(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        try {
            return checkNotNull(mqttClientCredentialsService.getCredentialsById(toUUID(strCredentialsId)).orElse(null));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/client/credentials/{credentialsId}", method = RequestMethod.DELETE)
    public void deleteCredentials(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        try {
            UUID uuid = toUUID(strCredentialsId);
            MqttClientCredentials mqttClientCredentials = checkClientCredentialsId(uuid);
            if (BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(mqttClientCredentials.getName())) {
                throw new ThingsboardException("System WebSocket MQTT client credentials can not be deleted!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            mqttClientCredentialsService.deleteCredentials(uuid);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/client/credentials/{credentialsId}", method = RequestMethod.POST)
    public MqttClientCredentials changeMqttBasicCredentialsPassword(@PathVariable("credentialsId") String strCredentialsId,
                                                                    @RequestBody ChangePasswordRequest changePasswordRequest) throws ThingsboardException {
        try {
            checkParameter("credentialsId", strCredentialsId);
            checkNotNull(changePasswordRequest);
            MqttClientCredentials mqttClientCredentials = getCredentialsById(strCredentialsId);
            if (mqttClientCredentials.getCredentialsType() != ClientCredentialsType.MQTT_BASIC) {
                throw new ThingsboardException("MQTT credentials should be of 'MQTT_BASIC' type!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }

            BasicMqttCredentials basicMqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(mqttClientCredentials, BasicMqttCredentials.class);

            validatePassword(passwordEncoder, changePasswordRequest, basicMqttCredentials.getPassword());

            basicMqttCredentials.setPassword(encodePasswordIfNotEmpty(changePasswordRequest.getNewPassword()));
            mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));

            return checkNotNull(mqttClientCredentialsService.saveCredentials(mqttClientCredentials));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/client/credentials/info", method = RequestMethod.GET)
    @ResponseBody
    public ClientCredentialsInfoDto getClientCredentialsStatsInfo() throws ThingsboardException {
        try {
            return checkNotNull(mqttClientCredentialsService.getClientCredentialsInfo());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/v2/mqtt/client/credentials", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ShortMqttClientCredentials> getCredentialsV2(@RequestParam int pageSize,
                                                                 @RequestParam int page,
                                                                 @RequestParam(required = false) String textSearch,
                                                                 @RequestParam(required = false) String sortProperty,
                                                                 @RequestParam(required = false) String sortOrder,
                                                                 @RequestParam(required = false) String[] clientTypeList,
                                                                 @RequestParam(required = false) String[] credentialsTypeList) throws ThingsboardException {
        try {
            List<ClientType> clientTypes = new ArrayList<>();
            if (clientTypeList != null) {
                for (String strType : clientTypeList) {
                    if (!StringUtils.isEmpty(strType)) {
                        clientTypes.add(ClientType.valueOf(strType));
                    }
                }
            }
            List<ClientCredentialsType> credentialsTypes = new ArrayList<>();
            if (credentialsTypeList != null) {
                for (String strCredentialsType : credentialsTypeList) {
                    if (!StringUtils.isEmpty(strCredentialsType)) {
                        credentialsTypes.add(ClientCredentialsType.valueOf(strCredentialsType));
                    }
                }
            }
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);

            return checkNotNull(mqttClientCredentialsService.getCredentialsV2(new ClientCredentialsQuery(pageLink, clientTypes, credentialsTypes)));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/mqtt/client/credentials", params = {"name"}, method = RequestMethod.GET)
    @ResponseBody
    public MqttClientCredentials getClientCredentialsByName(@RequestParam String name) throws ThingsboardException {
        try {
            return checkNotNull(mqttClientCredentialsService.findCredentialsByName(name));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private String encodePasswordIfNotEmpty(String password) {
        return StringUtils.isEmpty(password) ? null : passwordEncoder.encode(password);
    }
}
