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
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.DelegatingServletOutputStream;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBodyReturnValueHandler;
import org.thingsboard.mqtt.broker.dao.store.BackupInProgressException;
import org.thingsboard.mqtt.broker.dao.store.StoreBackup;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponseHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The download of a backup through Spring's real async machinery, with an executor the test runs by hand: the backup
 * slot is taken in the handler, released when the request ends without the body ever running, and the request has
 * no async timeout. A failure of the broker while the archive streams goes through the dispatcher of a standalone
 * MockMvc.
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
        IOException reset = new IOException("Connection reset by peer");
        download(new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new DelegatingServletOutputStream(new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        throw reset;
                    }
                });
            }
        });

        executor.runAll();

        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        // the IOException itself, which Spring marks as a gone client: no failure of the broker
        assertThat((Throwable) WebAsyncUtils.getAsyncManager(request).getConcurrentResult())
                .isInstanceOf(AsyncRequestNotUsableException.class)
                .rootCause().isSameAs(reset);
    }

    @Test
    void aFailureOfTheBrokerMidStreamIsSurfacedAsABackupStreamFailure() throws Exception {
        storeBackup.failure = new IOException("Input/output error");
        download();

        executor.runAll();

        assertThat(storeBackup.held).isFalse();
        assertThat(storeBackup.checkpoint).as("the temporary checkpoint").doesNotExist();
        assertThat((Throwable) WebAsyncUtils.getAsyncManager(request).getConcurrentResult())
                .isInstanceOf(StandaloneBackupController.BackupStreamException.class)
                .cause().isSameAs(storeBackup.failure);
    }

    @Test
    void aFailureOfTheBrokerAfterTheResponseIsCommittedEscapesToTheContainer() throws Exception {
        storeBackup.partial = "partial".getBytes(StandardCharsets.UTF_8);
        storeBackup.failure = new IOException("Input/output error");
        MockMvc mockMvc = standaloneMockMvc();

        MvcResult started = mockMvc.perform(get("/api/system/backup")).andExpect(request().asyncStarted()).andReturn();
        Throwable thrown = catchThrowable(() -> mockMvc.perform(asyncDispatch(started)));

        // not handled: the container aborts the connection instead of ending the 200 with its final chunk
        assertThat(thrown).isInstanceOf(ServletException.class)
                .cause().isInstanceOf(StandaloneBackupController.BackupStreamException.class)
                .cause().isSameAs(storeBackup.failure);
        MockHttpServletResponse response = started.getResponse();
        assertThat(response.isCommitted()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("partial");
        assertThat(storeBackup.held).isFalse();
    }

    @Test
    void aFailureOfTheBrokerAfterTheResponseIsCommittedAbortsTheConnectionOfARealContainer() throws Exception {
        // more than the response buffer, so part of the archive is on the wire before the failure
        storeBackup.partial = new byte[64 * 1024];
        storeBackup.failure = new IOException("Input/output error");
        ReflectionTestUtils.setField(controller, "errorResponseHandler", new ThingsboardErrorResponseHandler());
        Tomcat tomcat = startTomcat();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest download = HttpRequest.newBuilder(URI.create("http://localhost:" + tomcat.getConnector().getLocalPort()
                    + "/api/system/backup")).build();

            // the response never ends with its final chunk: the client sees a cut stream, not a whole 200
            assertThatThrownBy(() -> client.send(download, HttpResponse.BodyHandlers.ofByteArray()))
                    .isInstanceOf(IOException.class);
            assertThat(storeBackup.held).isFalse();

            // the same download without the failure ends cleanly
            storeBackup.failure = null;
            HttpResponse<byte[]> whole = client.send(download, HttpResponse.BodyHandlers.ofByteArray());
            assertThat(whole.statusCode()).isEqualTo(200);
            assertThat(whole.body()).hasSize(64 * 1024 + "archive".length());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    void aFailureOfTheBrokerBeforeTheResponseIsCommittedIsAnErrorResponse() throws Exception {
        storeBackup.failure = new IOException("Input/output error");
        MockMvc mockMvc = standaloneMockMvc();

        MvcResult started = mockMvc.perform(get("/api/system/backup")).andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(header().doesNotExist("x-filename"))
                .andExpect(jsonPath("$.message").value("Input/output error"));
        assertThat(storeBackup.held).isFalse();
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
        return download(new MockHttpServletResponse());
    }

    private MockHttpServletResponse download(MockHttpServletResponse response) throws Exception {
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

    /**
     * An embedded Tomcat on a free port with Spring MVC's dispatcher in front of the controller, as the broker serves
     * it; the controller is a ready singleton, so nothing is injected into it.
     */
    private Tomcat startTomcat() throws Exception {
        GenericWebApplicationContext web = new GenericWebApplicationContext();
        AnnotationConfigUtils.registerAnnotationConfigProcessors(web);
        // registered here, not as a nested @Configuration: the broker's test contexts scan this package, and an
        // @EnableWebMvc class there switches Spring Boot's MVC auto-configuration off in all of them
        web.registerBean(DelegatingWebMvcConfiguration.class);
        web.getBeanFactory().registerSingleton("standaloneBackupController", controller);
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createDirectories(backups.resolve("tomcat")).toString());
        tomcat.setPort(0);
        Context context = tomcat.addContext("", null);
        Wrapper dispatcher = Tomcat.addServlet(context, "dispatcher", new DispatcherServlet(web));
        dispatcher.setAsyncSupported(true);
        dispatcher.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "dispatcher");
        // the global error page Spring Boot registers: Tomcat includes it into a committed response, then closes
        ErrorPage errorPage = new ErrorPage();
        errorPage.setLocation("/error");
        context.addErrorPage(errorPage);
        tomcat.start();
        return tomcat;
    }

    /**
     * The controller behind Spring MVC's dispatcher, with the shared error handling of every controller.
     */
    private MockMvc standaloneMockMvc() {
        ReflectionTestUtils.setField(controller, "errorResponseHandler", new ThingsboardErrorResponseHandler());
        return MockMvcBuilders.standaloneSetup(controller).build();
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
        private byte[] partial;
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
                        if (partial != null) {
                            out.write(partial);
                            out.flush();
                        }
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
