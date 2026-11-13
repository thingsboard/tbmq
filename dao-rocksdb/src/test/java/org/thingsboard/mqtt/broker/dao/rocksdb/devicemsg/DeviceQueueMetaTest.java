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
package org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceQueueMetaTest {

    @Test
    void everyFieldRoundTripsInAFixed44ByteLayout() {
        var meta = new DeviceQueueMeta(11L, 22L, 33, 44, 55L, 66L);

        byte[] bytes = meta.toBytes();

        assertThat(bytes).hasSize(44);
        assertThat(DeviceQueueMeta.fromBytes(bytes)).isEqualTo(meta);
    }

    @Test
    void emptyIsAllZero() {
        assertThat(DeviceQueueMeta.empty()).isEqualTo(new DeviceQueueMeta(0, 0, 0, 0, 0, 0));
        assertThat(DeviceQueueMeta.fromBytes(DeviceQueueMeta.empty().toBytes())).isEqualTo(DeviceQueueMeta.empty());
    }

    @Test
    void aWrongLengthIsRejected() {
        assertThatThrownBy(() -> DeviceQueueMeta.fromBytes(new byte[10])).isInstanceOf(StoreException.class);
    }

}
