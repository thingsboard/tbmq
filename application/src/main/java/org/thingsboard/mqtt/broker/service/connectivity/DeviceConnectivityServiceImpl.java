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
package org.thingsboard.mqtt.broker.service.connectivity;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ResourceUtils;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceConnectivityServiceImpl implements DeviceConnectivityService {

    @Value("${device.connectivity.mqtts.pem_cert_file:}")
    private String mqttsRootCaCertFile;
    @Value("${device.connectivity.mqtts.pem_cert_file_name:tbmq-ca-cert.pem}")
    private String mqttsRootCaCertFileName;

    private Resource rootCaCert;

    @PostConstruct
    public void init() {
        rootCaCert = getRootCaCert(mqttsRootCaCertFile);
    }

    @Override
    public Resource getRootCaCertFile(String protocol) {
        return rootCaCert;
    }

    @Override
    public String getMqttsRootCaCertFileName() {
        return mqttsRootCaCertFileName;
    }

    private Resource getRootCaCert(String path) {
        if (StringUtils.isBlank(path) || !ResourceUtils.resourceExists(this, path)) {
            return null;
        }

        StringBuilder pemContentBuilder = new StringBuilder();

        try (InputStream inStream = ResourceUtils.getInputStream(this, path);
             PEMParser pemParser = new PEMParser(new InputStreamReader(inStream))) {

            Object object;

            while ((object = pemParser.readObject()) != null) {
                if (object instanceof X509CertificateHolder certHolder) {
                    String certBase64 = Base64.getEncoder().encodeToString(certHolder.getEncoded());

                    pemContentBuilder.append("-----BEGIN CERTIFICATE-----\n");
                    int index = 0;
                    while (index < certBase64.length()) {
                        pemContentBuilder.append(certBase64, index, Math.min(index + 64, certBase64.length()));
                        pemContentBuilder.append("\n");
                        index += 64;
                    }
                    pemContentBuilder.append("-----END CERTIFICATE-----\n");
                }
            }
        } catch (Exception e) {
            String msg = String.format("Failed to read %s server certificate!", path);
            log.warn(msg);
            throw new RuntimeException(msg, e);
        }

        return new ByteArrayResource(pemContentBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }
}
