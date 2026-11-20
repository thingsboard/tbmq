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
package org.thingsboard.mqtt.broker.standalone.state;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StoreStateSupportTest {

    private final List<StoreException> loggedByCaller = new ArrayList<>();
    private BasicCallback callback;
    private Logger supportLogger;
    private ListAppender<ILoggingEvent> logged;

    @BeforeEach
    void setUp() {
        callback = mock(BasicCallback.class);
        supportLogger = (Logger) LoggerFactory.getLogger(StoreStateSupport.class);
        logged = new ListAppender<>();
        logged.start();
        supportLogger.addAppender(logged);
        supportLogger.setAdditive(false);
        supportLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        supportLogger.detachAppender(logged);
        supportLogger.setAdditive(true);
        supportLogger.setLevel(null);
    }

    @Test
    void aFullStoreIsLoggedAtDebugWithoutATraceAndReachesTheCallback() {
        StoreFullException full = new StoreFullException("Not enough free disk space");

        StoreStateSupport.writeThenCallback(() -> {
            throw full;
        }, callback, loggedByCaller::add);

        assertThat(loggedByCaller).isEmpty();
        verify(callback).onFailure(full);
        verify(callback, never()).onSuccess();
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains("store is full").endsWith("Not enough free disk space");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void anyOtherStoreErrorGoesToTheCallersLogAndReachesTheCallback() {
        StoreException failure = new StoreException("put failed");

        StoreStateSupport.writeThenCallback(() -> {
            throw failure;
        }, callback, loggedByCaller::add);

        assertThat(loggedByCaller).containsExactly(failure);
        verify(callback).onFailure(failure);
        verify(callback, never()).onSuccess();
        assertThat(logged.list).isEmpty();
    }

}
