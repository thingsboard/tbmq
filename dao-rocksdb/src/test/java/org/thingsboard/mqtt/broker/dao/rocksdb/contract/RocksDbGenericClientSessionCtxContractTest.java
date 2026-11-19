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
package org.thingsboard.mqtt.broker.dao.rocksdb.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.generic.GenericClientSessionCtxServiceImpl;
import org.thingsboard.mqtt.broker.dao.contract.AbstractGenericClientSessionCtxContractTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = {RocksDbContractTestConfig.class, RocksDbGenericClientSessionCtxContractTest.Services.class},
        loader = AnnotationConfigContextLoader.class)
public class RocksDbGenericClientSessionCtxContractTest extends AbstractGenericClientSessionCtxContractTest {

    private static final ObjectMapper TREES = new ObjectMapper();
    private static final String GOLDEN = "/storage-golden/generic-client-session-ctx.json";

    @Configuration
    @Import(GenericClientSessionCtxServiceImpl.class)
    static class Services {
    }

    @Autowired
    private RocksDbStore store;

    // The layout of the stored entry is the store's own: the SQL table has a row, not a key and a value.
    @Test
    public void aSavedContextIsOneEntryKeyedByTheClientIdWithTheGoldenValue() throws IOException {
        byte[] golden = goldenBytes();
        GenericClientSessionCtx ctx = TREES.readValue(golden, GenericClientSessionCtx.class);
        assertThat(ctx.getClientId()).isEqualTo("session-client-1");
        ctx.setQos2PublishPacketIds(new TreeSet<>(Set.of(5, 9)));
        byte[] key = ctx.getClientId().getBytes(StandardCharsets.UTF_8);
        try {
            genericClientSessionCtxService.saveGenericClientSessionCtx(ctx);

            byte[] stored = store.get(StoreColumnFamily.SESSION_CTX, key);
            assertThat(stored).isNotNull();
            // the format byte, then the JSON of the golden file
            assertThat(stored[0]).isEqualTo((byte) 1);
            JsonNode storedTree = TREES.readTree(stored, 1, stored.length - 1);
            assertThat(storedTree).isEqualTo(TREES.readTree(golden));
        } finally {
            genericClientSessionCtxDao.remove(ctx.getClientId());
        }
        assertThat(store.get(StoreColumnFamily.SESSION_CTX, key)).isNull();
    }

    private static byte[] goldenBytes() throws IOException {
        try (InputStream in = RocksDbGenericClientSessionCtxContractTest.class.getResourceAsStream(GOLDEN)) {
            assertThat(in).as("golden file %s", GOLDEN).isNotNull();
            return in.readAllBytes();
        }
    }

}
