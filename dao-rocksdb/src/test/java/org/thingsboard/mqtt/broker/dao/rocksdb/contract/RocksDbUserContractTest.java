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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.contract.AbstractUserContractTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreBatch;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityKeys;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.StorageObjectMapper;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@ContextConfiguration(classes = {RocksDbContractTestConfig.class, RocksDbWebSocketServices.class},
        loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = RocksDbWebSocketServices.TOPIC_PROPERTIES)
public class RocksDbUserContractTest extends AbstractUserContractTest {

    private static final String SYSADMIN_EMAIL = "sysadmin@thingsboard.org";
    private static final String SYSADMIN_PASSWORD = "sysadmin";

    // real calls, except where a case stubs a write
    @MockitoSpyBean
    private RocksDbStore rocksDbStore;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * The sysadmin that {@code system-data.sql} gives the PostgreSQL contract classes: the cases of the abstract class
     * expect it, and the store starts empty.
     */
    @Before
    public void seedSysadmin() {
        if (userService.findUserByEmail(SYSADMIN_EMAIL) != null) {
            return;
        }
        User sysadmin = new User();
        sysadmin.setAuthority(Authority.SYS_ADMIN);
        sysadmin.setEmail(SYSADMIN_EMAIL);
        sysadmin = userService.saveUser(sysadmin);
        UserCredentials credentials = userService.findUserCredentialsByUserId(sysadmin.getId());
        userService.saveUserCredentials(enabled(credentials, passwordEncoder.encode(SYSADMIN_PASSWORD)));
    }

    // Review Focus 4: the replace is one store write that holds both the delete of the old credentials and the put of
    // the new ones, so a crash inside it leaves the old credentials, and the sysadmin can still log in
    @Test
    public void aCrashInsideReplaceCannotLockTheSysadminOut() {
        User sysadmin = userService.findUserByEmail(SYSADMIN_EMAIL);
        UserCredentials old = userService.findUserCredentialsByUserId(sysadmin.getId());
        AtomicInteger writes = new AtomicInteger();
        List<RecordedWrite> batch = new ArrayList<>();
        doAnswer(invocation -> {
            writes.incrementAndGet();
            Consumer<StoreBatch> body = invocation.getArgument(1);
            body.accept(recordingBatch(batch));
            throw new StoreException("A crash inside the write");
        }).when(rocksDbStore).write(any(), any());
        try {
            assertThatThrownBy(() -> userService.replaceUserCredentials(enabled(old, passwordEncoder.encode("new password"))))
                    .isInstanceOf(StoreException.class);
        } finally {
            Mockito.reset(rocksDbStore);
        }

        assertThat(writes).hasValue(1);
        byte[] userIdKey = EntityKeys.uniqueIndexKey(EntityIndex.USER_CREDENTIALS_USER_ID, sysadmin.getId().toString());
        int oldDocumentDelete = indexOf(batch, w -> w.isDelete() && w.cf() == StoreColumnFamily.ENTITY && endsWith(w.key(), uuidBytes(old.getId())));
        int newDocumentPut = indexOf(batch, w -> !w.isDelete() && w.cf() == StoreColumnFamily.ENTITY
                && isNewCredentialsOf(w.value(), sysadmin.getId(), old.getId()));
        int oldUserIdDelete = indexOf(batch, w -> w.isDelete() && w.cf() == StoreColumnFamily.ENTITY_INDEX && Arrays.equals(w.key(), userIdKey));
        int newUserIdPut = indexOf(batch, w -> !w.isDelete() && w.cf() == StoreColumnFamily.ENTITY_INDEX && Arrays.equals(w.key(), userIdKey));
        assertThat(oldDocumentDelete).as("the delete of the old credentials").isNotNegative();
        assertThat(newDocumentPut).as("the put of the new credentials").isNotNegative();
        assertThat(oldUserIdDelete).as("the delete of the old user id entry").isNotNegative().isLessThan(newUserIdPut);

        UserCredentials found = userService.findUserCredentialsByUserId(sysadmin.getId());
        assertThat(found.getId()).isEqualTo(old.getId());
        assertThat(found.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches(SYSADMIN_PASSWORD, found.getPassword())).isTrue();
        assertThat(userCredentialsDao.findById(old.getId())).isEqualTo(found);
        assertThat(userCredentialsDao.find().stream().filter(c -> sysadmin.getId().equals(c.getUserId())))
                .extracting(UserCredentials::getId).containsExactly(old.getId());
    }

    private static StoreBatch recordingBatch(List<RecordedWrite> writes) {
        StoreBatch batch = mock(StoreBatch.class);
        doAnswer(invocation -> writes.add(new RecordedWrite(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))))
                .when(batch).put(any(), any(), any());
        doAnswer(invocation -> writes.add(new RecordedWrite(invocation.getArgument(0), invocation.getArgument(1), null)))
                .when(batch).delete(any(), any());
        return batch;
    }

    private static boolean isNewCredentialsOf(byte[] value, UUID userId, UUID oldId) {
        UserCredentials credentials = StorageObjectMapper.read(value, UserCredentials.class);
        return userId.equals(credentials.getUserId()) && !oldId.equals(credentials.getId());
    }

    private static int indexOf(List<RecordedWrite> writes, Predicate<RecordedWrite> match) {
        for (int i = 0; i < writes.size(); i++) {
            if (match.test(writes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    private static boolean endsWith(byte[] key, byte[] suffix) {
        return key.length >= suffix.length
                && Arrays.equals(key, key.length - suffix.length, key.length, suffix, 0, suffix.length);
    }

    // a null value is a delete
    private record RecordedWrite(StoreColumnFamily cf, byte[] key, byte[] value) {

        boolean isDelete() {
            return value == null;
        }

    }

}
