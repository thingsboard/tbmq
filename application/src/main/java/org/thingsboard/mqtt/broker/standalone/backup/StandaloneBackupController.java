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

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.controller.BaseController;
import org.thingsboard.mqtt.broker.dao.store.BackupInProgressException;
import org.thingsboard.mqtt.broker.dao.store.StoreBackup;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

/**
 * Downloads a consistent backup of the store as {@code tbmq-lite-backup-<yyyyMMdd-HHmmss>.tar.gz} (UTC). To restore
 * it: stop the broker, replace the {@code db} directory of the data volume with the one of the archive
 * ({@code rm -rf /data/db && tar -xzf <archive> -C /data db}) and start the broker.
 * <p>
 * The single backup slot and the checkpoint of the store are taken in the handler, so a second download while one runs
 * (409) or a checkpoint that cannot be taken (500, e.g. on a full disk) fails before the response starts. The archive is
 * streamed afterwards on the async executor, without the async request timeout; the slot is released and the
 * checkpoint deleted when the stream ends or, when it never starts, when the request ends.
 */
@Slf4j
@RestController
@StandaloneComponent
@RequiredArgsConstructor
@RequestMapping("/api/system")
public class StandaloneBackupController extends BaseController {

    // a timeout of 0 or less is none for the servlet container; -1 is also "none" for MockMvc
    static final long NO_ASYNC_TIMEOUT = -1;

    private static final String FILENAME_HEADER = "x-filename";

    private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final Object INTERCEPTOR_KEY = StandaloneBackupController.class.getName() + ".reservation";

    private final StoreBackup storeBackup;

    /**
     * @throws BackupInProgressException while another backup runs, mapped to 409
     */
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/backup")
    public ResponseEntity<StreamingResponseBody> downloadBackup(HttpServletRequest request) {
        StoreBackup.Reservation reservation = storeBackup.reserve();
        try {
            WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(INTERCEPTOR_KEY, new ReservationInterceptor(reservation));
        } catch (RuntimeException e) {
            reservation.close();
            throw e;
        }
        StreamingResponseBody body = out -> {
            try (reservation) {
                reservation.writeTo(out);
            } catch (IOException e) {
                // most often the client went away; the exception still ends the request
                log.warn("The backup download ended early: {}", e.toString());
                throw e;
            }
        };
        String fileName = "tbmq-lite-backup-" + FILE_TIMESTAMP.format(Instant.now()) + ".tar.gz";
        return ResponseEntity.ok()
                .contentType(APPLICATION_GZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                // the name the UI's download helper reads
                .header(FILENAME_HEADER, fileName)
                .body(body);
    }

    /**
     * Lifts the async request timeout of the download, and releases the backup slot when the request ends; the
     * release is a no-op once the body has started, which releases it itself when it ends.
     */
    private record ReservationInterceptor(StoreBackup.Reservation reservation) implements CallableProcessingInterceptor {

        @Override
        public <T> void beforeConcurrentHandling(NativeWebRequest request, Callable<T> task) {
            if (request instanceof AsyncWebRequest asyncWebRequest) {
                asyncWebRequest.setTimeout(NO_ASYNC_TIMEOUT);
            }
        }

        @Override
        public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
            reservation.close();
        }

    }

}
