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
package org.thingsboard.mqtt.broker.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Service
public class ServiceInfoProviderImpl implements ServiceInfoProvider {
    @Value("${service.id:#{null}}")
    private String serviceId;
    
    private QueueProtos.ServiceInfo serviceInfo;
    
    @PostConstruct
    public void init() {
        if (StringUtils.isEmpty(serviceId)) {
            serviceId = generateServiceId();
        }
        log.info("Current Service ID: {}", serviceId);
        this.serviceInfo = QueueProtos.ServiceInfo.newBuilder()
                .setServiceId(serviceId)
                .build();
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public QueueProtos.ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    private String generateServiceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return org.apache.commons.lang3.RandomStringUtils.randomAlphabetic(10);
        }
    }
}
