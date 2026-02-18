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
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.config.annotations.ApiOperation;
import org.thingsboard.mqtt.broker.service.connectivity.DeviceConnectivityService;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DeviceConnectivityController extends BaseController {

    private final DeviceConnectivityService deviceConnectivityService;


    @ApiOperation(value = "Download CA certificate using file path defined in device.connectivity properties (downloadRootCaCertificate)", notes = "Download root CA certificate.")
    @GetMapping(value = "/device-connectivity/mqtts/certificate/download")
    public ResponseEntity<Resource> downloadCaCertificate() throws ThingsboardException, IOException {
        var pemCert =
                checkNotNull(deviceConnectivityService.getPemCertFile("mqtts"), "mqtts root CA cert file is not found!");
        String rootCaCertFileName = deviceConnectivityService.getMqttsRootCaCertFileName();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + rootCaCertFileName)
                .header("x-filename", rootCaCertFileName)
                .contentLength(pemCert.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(pemCert);
    }

}
