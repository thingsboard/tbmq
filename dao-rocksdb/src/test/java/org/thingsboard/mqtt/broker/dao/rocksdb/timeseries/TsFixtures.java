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
package org.thingsboard.mqtt.broker.dao.rocksdb.timeseries;

import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;

/**
 * The {@code ts} and {@code ts_latest} layouts of {@link TsKeys} and {@link TsValueCodec}, for tests outside this
 * package that write time series entries as the DAOs do.
 */
public final class TsFixtures {

    private TsFixtures() {
    }

    public static byte[] tsKey(String entityId, int keyId, long ts) {
        return TsKeys.tsKey(entityId, keyId, ts);
    }

    public static byte[] tsValue(KvEntry kv) {
        return TsValueCodec.encode(kv);
    }

    public static byte[] latestKey(String entityId, int keyId) {
        return TsKeys.latestKey(entityId, keyId);
    }

    public static byte[] latestValue(TsKvEntry entry) {
        return TsValueCodec.encodeLatest(entry);
    }

}
