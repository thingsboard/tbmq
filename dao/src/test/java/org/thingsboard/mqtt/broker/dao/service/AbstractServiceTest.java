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
package org.thingsboard.mqtt.broker.dao.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.id.HasId;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AbstractServiceTest.class, loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Configuration
@ComponentScan("org.thingsboard.mqtt.broker")
//@ComponentScan(basePackages = "org.thingsboard.mqtt.broker", excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.thingsboard\\.mqtt\\.broker\\.queue\\..*"))
//@Import(DaoGlobalMockConfig.class)
public abstract class AbstractServiceTest {

    public static class IdComparator<D extends HasId> implements Comparator<D> {
        @Override
        public int compare(D o1, D o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }

    protected LifecycleEvent generateEvent(UUID entityId) throws IOException {
        return LifecycleEvent.builder()
                .entityId(entityId)
                .serviceId("server A")
                .success(true)
                .lcEventType("TEST")
                .build();
    }

    public JsonNode readFromResource(String resourceName) throws IOException {
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return JacksonUtil.fromBytes(Objects.requireNonNull(is).readAllBytes());
        }
    }
}
