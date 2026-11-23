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
package org.thingsboard.mqtt.broker.standalone.backup;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.MethodParameter;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBodyReturnValueHandler;
import org.thingsboard.mqtt.broker.dao.store.BackupInProgressException;
import org.thingsboard.mqtt.broker.dao.store.StoreBackup;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The download of a backup through Spring's real async machinery, with an executor the test runs by hand: the backup
 * slot is taken in the handler, released when the request ends without the body ever running, and the request has
 * no async timeout.
 */
class StandaloneBackupControllerAsyncTest {

    // what spring.mvc.async.request-timeout sets on every async request
    private static final long GLOBAL_ASYNC_TIMEOUT_MS = 30000;

    @TempDir
    Path backups;

    private FakeStoreBackup storeBackup;
    private final HeldExecutor executor = new HeldExecutor();
    private StandaloneBackupController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void startRequest() {
        storeBackup = new FakeStoreBackup(backups);
        controller = new StandaloneBackupController(storeBackup);
        request = new MockHttpServletRequest("GET", "/api/system/backup");
        request.setAsyncSupported(true);
    }

    @Test
    void theRequestHasNoAsyncTimeout() throws Exception {
        download();

        assertThat(request.getAsyncContext().getTimeout()).isEqualTo(StandaloneBackupController.NO_ASYNC_TIMEOUT);
    }

    @Test
    void theSlotIsReleasedWhenTheRequestCompletesBeforeTheBodyRuns() throws Exception {
        download();
        assertThat(storeBackup.held).isTrue();

        asyncContext().complete();

        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        assertThat(storeBackup.written).isZero();
    }

    @Test
    void theSlotIsReleasedWhenTheRequestTimesOutBeforeTheBodyRuns() throws Exception {
        download();

        for (AsyncListener listener : List.copyOf(asyncContext().getListeners())) {
            listener.onTimeout(new AsyncEvent(asyncContext()));
        }
        asyncContext().complete();

        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        assertThat(executor.tasks).singleElement().satisfies(task -> assertThat(task.isCancelled()).isTrue());
        assertThat(storeBackup.written).isZero();
    }

    @Test
    void theSlotIsReleasedWhenTheRequestFailsBeforeTheBodyRuns() throws Exception {
        download();

        for (AsyncListener listener : List.copyOf(asyncContext().getListeners())) {
            listener.onError(new AsyncEvent(asyncContext(), new IOException("Broken pipe")));
        }
        asyncContext().complete();

        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        assertThat(storeBackup.written).isZero();
    }

    @Test
    void theBodyWritesTheBackupAndReleasesTheSlot() throws Exception {
        MockHttpServletResponse response = download();

        executor.runAll();

        assertThat(storeBackup.written).isEqualTo(1);
        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        assertThat(response.getContentAsString()).isEqualTo("archive");
        assertThat(WebAsyncUtils.getAsyncManager(request).getConcurrentResult()).isNull();
    }

    @Test
    void aClientGoneMidStreamReleasesTheSlotAndSurfacesTheIoException() throws Exception {
        storeBackup.failure = new IOException("Connection reset by peer");
        download();

        executor.runAll();

        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        assertThat(WebAsyncUtils.getAsyncManager(request).getConcurrentResult()).isSameAs(storeBackup.failure);
    }

    @Test
    void aSecondDownloadIsRefusedInTheHandler() throws Exception {
        download();

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/system/backup");
        second.setAsyncSupported(true);
        assertThatThrownBy(() -> controller.downloadBackup(second)).isInstanceOf(BackupInProgressException.class);
        assertThat(storeBackup.held).as("the first download keeps its slot").isTrue();
    }

    /**
     * What the dispatcher does for the request: an async request with the global timeout, the handler, then the
     * return value handler, which starts the async processing with the body queued on the executor.
     */
    private MockHttpServletResponse download() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        StandardServletAsyncWebRequest asyncWebRequest = new StandardServletAsyncWebRequest(request, response);
        asyncWebRequest.setTimeout(GLOBAL_ASYNC_TIMEOUT_MS);
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        asyncManager.setTaskExecutor(executor);
        asyncManager.setAsyncWebRequest(asyncWebRequest);

        ResponseEntity<StreamingResponseBody> entity = controller.downloadBackup(request);

        MethodParameter returnType = new MethodParameter(
                StandaloneBackupController.class.getMethod("downloadBackup", HttpServletRequest.class), -1);
        new StreamingResponseBodyReturnValueHandler().handleReturnValue(entity, returnType, new ModelAndViewContainer(), asyncWebRequest);
        assertThat(request.isAsyncStarted()).isTrue();
        return response;
    }

    private MockAsyncContext asyncContext() {
        return (MockAsyncContext) request.getAsyncContext();
    }

    /**
     * Holds the slot and a temporary checkpoint directory like the store backup does: both released when the
     * reservation is written with or closed unused.
     */
    private static final class FakeStoreBackup implements StoreBackup {

        private final Path backups;
        private boolean held;
        private int written;
        private IOException failure;
        private Path checkpoint;

        private FakeStoreBackup(Path backups) {
            this.backups = backups;
        }

        @Override
        public Reservation reserve() {
            if (held) {
                throw new BackupInProgressException("A backup of the store is already in progress");
            }
            try {
                checkpoint = Files.createDirectory(backups.resolve("tmp-backup-" + UUID.randomUUID()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            held = true;
            return new Reservation() {
                private boolean used;

                @Override
                public void writeTo(OutputStream out) throws IOException {
                    used = true;
                    try {
                        if (failure != null) {
                            throw failure;
                        }
                        out.write("archive".getBytes());
                        written++;
                    } finally {
                        release();
                    }
                }

                @Override
                public void close() {
                    if (!used) {
                        used = true;
                        release();
                    }
                }
            };
        }

        private void release() {
            try {
                Files.deleteIfExists(checkpoint);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            held = false;
        }

    }

    /**
     * Queues the tasks; the test runs them, or never does.
     */
    private static final class HeldExecutor implements AsyncTaskExecutor {

        private final List<FutureTask<?>> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            submit(task);
        }

        @Override
        public Future<?> submit(Runnable task) {
            FutureTask<?> future = new FutureTask<>(task, null);
            tasks.add(future);
            return future;
        }

        void runAll() {
            tasks.forEach(FutureTask::run);
        }

    }

}
