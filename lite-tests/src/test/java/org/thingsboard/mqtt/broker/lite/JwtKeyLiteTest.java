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
package org.thingsboard.mqtt.broker.lite;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.FileSystemUtils;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST JWT signing key of the standalone edition is generated per install and kept in the store: a token issued
 * before a restart is valid after it, and the token of one install is not valid on another.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = JwtKeyLiteTest.class, loader = SpringBootContextLoader.class)
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class JwtKeyLiteTest extends AbstractControllerTest {

    @Test
    public void aTokenIssuedBeforeARestartStillWorks() throws Exception {
        loginSysAdmin();
        String issued = token;
        doGet("/api/auth/user").andExpect(status().isOk());

        restart();
        token = issued;

        doGet("/api/auth/user").andExpect(status().isOk());
    }

    @Test
    public void twoStoresSignWithDifferentKeys() throws Exception {
        // a second Spring context cannot run beside the first (same ports), so the one context is restarted on another
        // store directory, which is a second install with a key of its own
        String storePath = System.getProperty(TempStorePathRule.STORE_PATH);
        Path otherStore = Files.createTempDirectory("tbmq-lite-other-store");
        loginSysAdmin();
        String issued = token;
        try {
            System.setProperty(TempStorePathRule.STORE_PATH, otherStore.toString());
            restart();
            token = issued;

            doGet("/api/auth/user").andExpect(status().isUnauthorized());
            loginSysAdmin();
            doGet("/api/auth/user").andExpect(status().isOk());
        } finally {
            System.setProperty(TempStorePathRule.STORE_PATH, storePath);
            // the first store again, which releases the other one before its directory is removed
            restart();
            FileSystemUtils.deleteRecursively(otherStore);
        }
        token = issued;
        doGet("/api/auth/user").andExpect(status().isOk());
        assertThat(otherStore).doesNotExist();
    }

    private void restart() throws Exception {
        SpringRestarter.getInstance().restart();
        mockMvc = null;
        setupWebTest();
    }

}
