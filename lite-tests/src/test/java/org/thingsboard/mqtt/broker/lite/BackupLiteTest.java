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

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.lite.spec.RawMqttClient;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assume.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A backup downloaded from a running broker, extracted as the restore instructions do it ({@code tar -xzf <archive> db}),
 * starts a broker with the same state: the client credentials and the retained message written before the download.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = BackupLiteTest.class, loader = SpringBootContextLoader.class)
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class BackupLiteTest extends AbstractControllerTest {

    /**
     * The innermost class rule, so the property is set before the context starts.
     */
    @ClassRule(order = Integer.MAX_VALUE)
    public static final TempBackupsPathRule backups = new TempBackupsPathRule();

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CREDENTIALS_NAME = "backup-credentials";
    private static final String RETAINED_TOPIC = "backup/retained";

    @Autowired
    private RetainedMsgListenerService retainedMsgListenerService;
    @Autowired
    private RocksDbStore store;

    @Test
    public void aDownloadedBackupRestoresTheBrokerState() throws Exception {
        assumeTrue("the tar tool is not on the PATH", tarAvailable());
        loginSysAdmin();
        doPost("/api/mqtt/client/credentials", basicCredentials()).andExpect(status().isOk());
        try (RawMqttClient publisher = RawMqttClient.connectClean(mqttPort, "backup-publisher", MqttVersion.MQTT_5)) {
            assertThat(publisher.publishRetainedQos1(RETAINED_TOPIC, 1, "kept")).as("PUBACK reason code").isZero();
        }
        await("retained message stored").atMost(TIMEOUT).until(() -> retainedMsgListenerService.getRetainedMsgForTopic(RETAINED_TOPIC) != null);
        UUID installId = store.meta().installId();

        MvcResult started = doGet("/api/system/backup").andExpect(request().asyncStarted()).andReturn();
        byte[] archive = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        Path restoreDir = Files.createTempDirectory("tbmq-lite-restore");
        String storePath = System.getProperty(TempStorePathRule.STORE_PATH);
        try {
            Path archiveFile = Files.write(restoreDir.resolve("backup.tar.gz"), archive);
            extract(archiveFile, restoreDir);
            JsonNode manifest = JacksonUtil.toJsonNode(Files.readString(restoreDir.resolve("backup-manifest.json")));
            assertThat(manifest.get("installId").asText()).isEqualTo(installId.toString());

            System.setProperty(TempStorePathRule.STORE_PATH, restoreDir.resolve("db").toString());
            restart();

            assertThat(store.meta().installId()).isEqualTo(installId);
            assertThat(mqttClientCredentialsService.findCredentialsByName(CREDENTIALS_NAME)).as("credentials").isNotNull();
            RetainedMsgDto retained = retainedMsgListenerService.getRetainedMsgForTopic(RETAINED_TOPIC);
            assertThat(retained).as("retained message").isNotNull();
            assertThat(retained.getPayload()).isEqualTo("kept");
        } finally {
            System.setProperty(TempStorePathRule.STORE_PATH, storePath);
            // the first store again, which releases the restored one before its directory is removed
            restart();
            FileSystemUtils.deleteRecursively(restoreDir);
        }
    }

    private void restart() throws Exception {
        SpringRestarter.getInstance().restart();
        mockMvc = null;
        setupWebTest();
    }

    /**
     * {@code tar -xzf <archive> -C <dir> db}: the store only, as the restore instructions extract it.
     */
    private static void extract(Path archive, Path dir) throws IOException, InterruptedException {
        Process tar = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", dir.toString(), "db", "backup-manifest.json")
                .redirectErrorStream(true).start();
        String output = new String(tar.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(tar.waitFor(60, TimeUnit.SECONDS)).as("tar ended").isTrue();
        assertThat(tar.exitValue()).as("tar exit code, output: %s", output).isZero();
    }

    private static boolean tarAvailable() {
        try {
            Process tar = new ProcessBuilder("tar", "--version").redirectErrorStream(true).start();
            tar.getInputStream().readAllBytes();
            return tar.waitFor(10, TimeUnit.SECONDS) && tar.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static MqttClientCredentials basicCredentials() {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName(CREDENTIALS_NAME);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsValue(JacksonUtil.toString(BasicMqttCredentials.newInstance(null, "backup-user", "secret", null)));
        return credentials;
    }

}
