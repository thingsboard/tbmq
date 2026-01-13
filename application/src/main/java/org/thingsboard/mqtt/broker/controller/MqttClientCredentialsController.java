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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.dto.ClientCredentialsInfoDto;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportRequest;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportResult;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.service.mqtt.client.credentials.MqttClientCredentialsBulkImportService;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MqttClientCredentialsController extends BaseController {

    private final BCryptPasswordEncoder passwordEncoder;
    private final MqttClientCredentialsBulkImportService bulkImportService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/mqtt/client/credentials")
    public MqttClientCredentials saveMqttClientCredentials(@RequestBody MqttClientCredentials mqttClientCredentials) throws ThingsboardException {
        if (mqttClientCredentials.isBasicCredentials()) {
            BasicMqttCredentials mqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(mqttClientCredentials, BasicMqttCredentials.class);
            if (mqttClientCredentials.getId() == null) {
                mqttCredentials.setPassword(encodePasswordIfNotEmpty(mqttCredentials.getPassword()));
            } else {
                MqttClientCredentials currentMqttClientCredentials = getMqttClientCredentialsById(mqttClientCredentials.getId());
                if (currentMqttClientCredentials != null && currentMqttClientCredentials.isBasicCredentials()) {
                    mqttCredentials.setPassword(getCurrentPassword(currentMqttClientCredentials));
                }
            }
            mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
        }

        return sanitizeSensitiveMqttCredsData(mqttClientCredentialsService.saveCredentials(mqttClientCredentials));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/client/credentials", params = {"pageSize", "page"})
    public PageData<ShortMqttClientCredentials> getCredentials(@RequestParam int pageSize,
                                                               @RequestParam int page,
                                                               @RequestParam(required = false) String textSearch,
                                                               @RequestParam(required = false) String sortProperty,
                                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return mqttClientCredentialsService.getCredentials(pageLink);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/client/credentials/{credentialsId}")
    public MqttClientCredentials getCredentialsById(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        return sanitizeSensitiveMqttCredsData(getMqttClientCredentialsById(strCredentialsId));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/mqtt/client/credentials/{credentialsId}")
    public void deleteCredentials(@PathVariable("credentialsId") String strCredentialsId) throws ThingsboardException {
        UUID uuid = toUUID(strCredentialsId);
        MqttClientCredentials mqttClientCredentials = checkClientCredentialsId(uuid);
        if (BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(mqttClientCredentials.getName())) {
            throw new ThingsboardException("System WebSocket MQTT client credentials can not be deleted!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        mqttClientCredentialsService.deleteCredentials(uuid);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/mqtt/client/credentials/{credentialsId}")
    public MqttClientCredentials changeMqttBasicCredentialsPassword(@PathVariable("credentialsId") String strCredentialsId,
                                                                    @RequestBody ChangePasswordRequest changePasswordRequest) throws ThingsboardException {
        checkParameter("credentialsId", strCredentialsId);
        checkNotNull(changePasswordRequest);
        MqttClientCredentials mqttClientCredentials = checkNotNull(getMqttClientCredentialsById(strCredentialsId));
        if (mqttClientCredentials.getCredentialsType() != ClientCredentialsType.MQTT_BASIC) {
            throw new ThingsboardException("MQTT credentials should be of 'MQTT_BASIC' type!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        BasicMqttCredentials basicMqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(mqttClientCredentials, BasicMqttCredentials.class);

        validatePassword(passwordEncoder, changePasswordRequest, basicMqttCredentials.getPassword());

        basicMqttCredentials.setPassword(encodePasswordIfNotEmpty(changePasswordRequest.getNewPassword()));
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));

        return sanitizeSensitiveMqttCredsData(mqttClientCredentialsService.saveCredentials(mqttClientCredentials));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/client/credentials/info")
    public ClientCredentialsInfoDto getClientCredentialsStatsInfo() throws ThingsboardException {
        return checkNotNull(mqttClientCredentialsService.getClientCredentialsInfo());
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/v2/mqtt/client/credentials", params = {"pageSize", "page"})
    public PageData<ShortMqttClientCredentials> getCredentialsV2(@RequestParam int pageSize,
                                                                 @RequestParam int page,
                                                                 @RequestParam(required = false) String textSearch,
                                                                 @RequestParam(required = false) String sortProperty,
                                                                 @RequestParam(required = false) String sortOrder,
                                                                 @RequestParam(required = false) String[] clientTypeList,
                                                                 @RequestParam(required = false) String[] credentialsTypeList,
                                                                 @RequestParam(required = false) String username,
                                                                 @RequestParam(required = false) String clientId,
                                                                 @RequestParam(required = false) String certificateCn) throws ThingsboardException {

        List<ClientType> clientTypes = parseEnumList(ClientType.class, clientTypeList);
        List<ClientCredentialsType> credentialsTypes = parseEnumList(ClientCredentialsType.class, credentialsTypeList);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);

        ClientCredentialsQuery query = new ClientCredentialsQuery(pageLink, clientTypes, credentialsTypes,
                username, clientId, certificateCn);
        return mqttClientCredentialsService.getCredentialsV2(query);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/mqtt/client/credentials", params = {"name"})
    public MqttClientCredentials getClientCredentialsByName(@RequestParam String name) throws ThingsboardException {
        checkParameter("name", name);
        return sanitizeSensitiveMqttCredsData(mqttClientCredentialsService.findCredentialsByName(name));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping("/mqtt/client/credentials/bulk_import")
    public BulkImportResult<MqttClientCredentials> processMqttClientCredentialsBulkImport(@RequestBody BulkImportRequest request) throws Exception {
        return bulkImportService.processBulkImport(request);
    }

    private String encodePasswordIfNotEmpty(String password) {
        return StringUtils.isEmpty(password) ? null : passwordEncoder.encode(password);
    }

    private MqttClientCredentials getMqttClientCredentialsById(String strCredentialsId) throws ThingsboardException {
        return getMqttClientCredentialsById(toUUID(strCredentialsId));
    }

    private MqttClientCredentials getMqttClientCredentialsById(UUID credentialsId) {
        return mqttClientCredentialsService.getCredentialsById(credentialsId).orElse(null);
    }

    private String getCurrentPassword(MqttClientCredentials currentCredentials) {
        return MqttClientCredentialsUtil.getMqttCredentials(currentCredentials, BasicMqttCredentials.class).getPassword();
    }
}
