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

import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.store.StoreBackup;
import org.thingsboard.mqtt.broker.lite.TempBackupsPathRule;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtTokenFactory;
import org.thingsboard.mqtt.broker.service.security.model.token.RawAccessJwtToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The backup download through the whole web stack of the Lite edition: security, the async response and the error
 * mapping.
 */
public class StandaloneBackupControllerTest extends AbstractControllerTest {

    private static final String BACKUP_PATH = "/api/system/backup";

    /**
     * The innermost class rule, so the property is set before the context starts.
     */
    @ClassRule(order = Integer.MAX_VALUE)
    public static final TempBackupsPathRule backups = new TempBackupsPathRule();

    @Autowired
    private StoreBackup storeBackup;
    @Autowired
    private JwtTokenFactory jwtTokenFactory;
    @Autowired
    private StoreProperties storeProperties;

    @Test
    public void anAnonymousRequestIsUnauthorized() throws Exception {
        logout();

        doGet(BACKUP_PATH).andExpect(status().isUnauthorized());

        storeBackup.reserve().close();
    }

    @Test
    public void onlyTheSysadminMayDownload() throws Exception {
        loginSysAdmin();
        // a valid access token of the sysadmin user, but with another authority
        SecurityUser sysadmin = jwtTokenFactory.parseAccessJwtToken(new RawAccessJwtToken(token));
        SecurityUser other = new SecurityUser(sysadmin.getId());
        other.setEmail(sysadmin.getEmail());
        other.setAuthority(Authority.REFRESH_TOKEN);
        other.setEnabled(true);
        token = jwtTokenFactory.createAccessJwtToken(other).getToken();

        doGet(BACKUP_PATH).andExpect(status().isForbidden());

        storeBackup.reserve().close();
    }

    @Test
    public void theSysadminDownloadsAGzipArchive() throws Exception {
        loginSysAdmin();

        MvcResult started = doGet(BACKUP_PATH).andExpect(request().asyncStarted()).andReturn();
        // spring.mvc.async.request-timeout does not cut a long download
        assertThat(started.getRequest().getAsyncContext().getTimeout()).isEqualTo(StandaloneBackupController.NO_ASYNC_TIMEOUT);
        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/gzip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        matchesPattern("attachment; filename=\"tbmq-lite-backup-\\d{8}-\\d{6}\\.tar\\.gz\"")))
                .andReturn();

        byte[] firstBlock;
        try (InputStream tar = new GZIPInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            firstBlock = tar.readNBytes(512);
        }
        assertThat(new String(firstBlock, 0, "backup-manifest.json".length(), StandardCharsets.UTF_8)).isEqualTo("backup-manifest.json");
        assertThat(tmpBackups()).as("temporary checkpoints left").isZero();
        // the slot is released
        storeBackup.reserve().close();
    }

    @Test
    public void aConcurrentDownloadIsRefusedWithConflict() throws Exception {
        loginSysAdmin();

        try (StoreBackup.Reservation running = storeBackup.reserve()) {
            doGet(BACKUP_PATH)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("A backup of the store is already in progress; try again once it finishes"));
        }

        MvcResult started = doGet(BACKUP_PATH).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }

    @Test
    public void aCheckpointThatCannotBeTakenFailsBeforeTheDownloadStarts() throws Exception {
        loginSysAdmin();
        String backupsPath = storeProperties.getBackups().getPath();
        // a regular file where the backups directory should be: the checkpoint cannot be created under it
        Path notADirectory = Files.write(backups.dir().resolve("not-a-directory"), new byte[]{1});
        try {
            storeProperties.getBackups().setPath(notADirectory.toString());

            doGet(BACKUP_PATH)
                    .andExpect(request().asyncNotStarted())
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value(containsString("checkpoint")));
        } finally {
            storeProperties.getBackups().setPath(backupsPath);
            Files.delete(notADirectory);
        }

        // the slot is free and no checkpoint is left
        assertThat(tmpBackups()).isZero();
        MvcResult started = doGet(BACKUP_PATH).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }

    private static long tmpBackups() throws IOException {
        try (Stream<Path> entries = Files.list(backups.dir())) {
            return entries.filter(path -> path.getFileName().toString().startsWith("tmp-backup-")).count();
        }
    }

}
