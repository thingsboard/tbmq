/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.data;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

@RunWith(SpringRunner.class)
public class UserPropertiesTest {

    @Test
    public void testUserPropertiesMapping() {
        MqttProperties.UserProperties mqttUserProperties = generateMqttUserProperties();

        String str = JacksonUtil.toString(UserProperties.newInstance(getMqttProperties(mqttUserProperties)));
        Assert.assertNotNull(str);

        UserProperties userProperties = JacksonUtil.fromString(str, UserProperties.class);
        Assert.assertNotNull(userProperties);

        MqttProperties.UserProperties mqttUserPropertiesNew = UserProperties.mapToMqttUserProperties(userProperties);

        Assert.assertEquals(mqttUserProperties, mqttUserPropertiesNew);
    }

    @Test
    public void testNullUserPropertiesMapping() {
        String str = JacksonUtil.toString(UserProperties.newInstance(getMqttProperties()));
        Assert.assertNull(str);

        UserProperties userProperties = JacksonUtil.fromString(str, UserProperties.class);
        Assert.assertNull(userProperties);

        MqttProperties.UserProperties mqttUserProperties = UserProperties.mapToMqttUserProperties(userProperties);
        Assert.assertNull(mqttUserProperties);
    }

    private static MqttProperties getMqttProperties(MqttProperties.UserProperties userProperties) {
        MqttProperties mqttProperties = new MqttProperties();
        mqttProperties.add(userProperties);
        return mqttProperties;
    }

    private static MqttProperties getMqttProperties() {
        return new MqttProperties();
    }

    private static MqttProperties.UserProperties generateMqttUserProperties() {
        MqttProperties.UserProperties userProperties = new MqttProperties.UserProperties();
        userProperties.add("one", "two");
        userProperties.add("key", "value");
        userProperties.add("testKey", "testValue");
        userProperties.add("country", "UA");
        userProperties.add("city", "Kyiv");
        userProperties.add("key", "valueNew");
        return userProperties;
    }
}