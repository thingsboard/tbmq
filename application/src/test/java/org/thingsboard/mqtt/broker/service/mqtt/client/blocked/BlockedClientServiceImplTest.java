/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientResult;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.IpAddressBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.UsernameBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.producer.BlockedClientProducerService;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockedClientServiceImplTest {

    private BlockedClientProducerService producerService;
    private BlockedClientServiceImpl service;

    private static final String CLIENT_ID = "clientId";
    private static final String USERNAME = "username";
    private static final String IP = "192.168.1.100";

    @BeforeEach
    void setUp() {
        producerService = mock(BlockedClientProducerService.class);
        ServiceInfoProvider serviceInfoProvider = mock(ServiceInfoProvider.class);
        when(serviceInfoProvider.getServiceId()).thenReturn("test-service");

        service = new BlockedClientServiceImpl(producerService, serviceInfoProvider);
    }

    @Test
    public void testConvertClientIdBlockedClient() {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "CLIENT_ID");
        objectNode.put("clientId", "some_value");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "your_description");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof ClientIdBlockedClient).isTrue();
    }

    @Test
    public void testConvertIpAddressBlockedClient() {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "IP_ADDRESS");
        objectNode.put("ipAddress", "some_value");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "your_description");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof IpAddressBlockedClient).isTrue();
    }

    @Test
    public void testConvertUsernameBlockedClient() {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "USERNAME");
        objectNode.put("username", "some_value");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "your_description");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof UsernameBlockedClient).isTrue();
    }

    @Test
    public void testDeserializeRegexBlockedClient_withValidRegex() {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "REGEX");
        objectNode.put("pattern", "^client-[0-9]+$");
        objectNode.put("regexMatchTarget", "BY_CLIENT_ID");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "valid regex test");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient).isInstanceOf(RegexBlockedClient.class);

        RegexBlockedClient regexBlockedClient = (RegexBlockedClient) blockedClient;
        assertThat(regexBlockedClient).isNotNull();
        assertThat(regexBlockedClient.getCompiledPattern()).isNotNull();
        assertThat(regexBlockedClient.matches("client-123")).isTrue();
        assertThat(regexBlockedClient.matches("user-123")).isFalse();
    }

    @Test
    public void testDeserializeRegexBlockedClient_withInvalidRegex() {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "REGEX");
        objectNode.put("pattern", "[invalid-regex");
        objectNode.put("regexMatchTarget", "BY_CLIENT_ID");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "invalid regex test");

        assertThatThrownBy(() -> JacksonUtil.convertValue(objectNode, BlockedClient.class))
                .isInstanceOf(IllegalArgumentException.class)
                .cause()
                .hasMessageContaining("Unclosed character class");
    }

    @Test
    public void testCheckBlockedPerformanceWithRegexRule_Parallel() throws InterruptedException {
        assertThat(service.getBlockedClients().get(BlockedClientType.REGEX)).isEmpty();

        RegexBlockedClient regexBlockedClient = new RegexBlockedClient(
                "^client-[0-9]+$", RegexMatchTarget.BY_CLIENT_ID
        );
        regexBlockedClient.setExpirationTime(System.currentTimeMillis() + 60_000);
        regexBlockedClient.setDescription("Parallel regex test");

        service.addBlockedClientAndPersist(regexBlockedClient);
        assertThat(service.getBlockedClients().get(BlockedClientType.REGEX)).hasSize(1);

        int threadCount = 4;
        int callsPerThread = 250_000;
        int totalCalls = threadCount * callsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalCalls);

        String matchingClientId = "client-123";
        String username = "user";
        String ipAddress = "127.0.0.1";

        AtomicInteger blockedCounter = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < totalCalls; i++) {
            executor.submit(() -> {
                try {
                    BlockedClientResult result = service.checkBlocked(matchingClientId, username, ipAddress);
                    if (result.isBlocked()) {
                        blockedCounter.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long durationNs = System.nanoTime() - startTime;
        executor.shutdownNow();

        double durationMs = durationNs / 1_000_000.0;

        System.out.println("Blocked " + blockedCounter.get() + " times out of " + totalCalls);
        System.out.printf("Total time: %.2f ms%n", durationMs);
        System.out.printf("Average per call: %.4f ms%n", durationMs / totalCalls);

        assertThat(blockedCounter.get()).isEqualTo(totalCalls);
        assertThat(durationMs).isLessThan(5000);
    }

    @Test
    public void testConvertRegexBlockedClient() {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "REGEX");
        objectNode.put("pattern", "some_value");
        objectNode.put("regexMatchTarget", "BY_CLIENT_ID");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "your_description");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof RegexBlockedClient).isTrue();
    }

    @Test
    public void testEqualsRegexBlockedClient() {
        RegexBlockedClient regexBlockedClient1 = new RegexBlockedClient(1, "asd", "one-two-three", RegexMatchTarget.BY_CLIENT_ID);
        RegexBlockedClient regexBlockedClient2 = new RegexBlockedClient(1, "asd", "one-two-three", RegexMatchTarget.BY_USERNAME);

        assertThat(regexBlockedClient1).isNotEqualTo(regexBlockedClient2);

        regexBlockedClient1 = new RegexBlockedClient(1, "description1", "1-2-3", RegexMatchTarget.BY_CLIENT_ID);
        regexBlockedClient2 = new RegexBlockedClient(2, "description1", "1-2-3", RegexMatchTarget.BY_CLIENT_ID);

        assertThat(regexBlockedClient1).isEqualTo(regexBlockedClient2);
    }

    @Test
    void testAddAndCheckExactMatchClientId() {
        BlockedClient client = new ClientIdBlockedClient(System.currentTimeMillis() + 60000, null, CLIENT_ID);
        service.addBlockedClient(client);

        BlockedClientResult result = service.checkBlocked(CLIENT_ID, null, null);
        assertTrue(result.isBlocked());
        assertEquals(client, result.getBlockedClient());
    }

    @Test
    void testAddAndCheckExactMatchUsername() {
        BlockedClient client = new UsernameBlockedClient(System.currentTimeMillis() + 60000, null, USERNAME);
        service.addBlockedClient(client);

        BlockedClientResult result = service.checkBlocked(null, USERNAME, null);
        assertTrue(result.isBlocked());
        assertEquals(client, result.getBlockedClient());
    }

    @Test
    void testAddAndCheckExactMatchIpAddress() {
        BlockedClient client = new IpAddressBlockedClient(System.currentTimeMillis() + 60000, null, IP);
        service.addBlockedClient(client);

        BlockedClientResult result = service.checkBlocked(null, null, IP);
        assertTrue(result.isBlocked());
        assertEquals(client, result.getBlockedClient());
    }

    @Test
    void testCheckRegexMatchByClientId() {
        RegexBlockedClient regexClient = new RegexBlockedClient(
                System.currentTimeMillis() + 60000, null, "client.*",
                RegexMatchTarget.BY_CLIENT_ID
        );
        service.addBlockedClient(regexClient);

        BlockedClientResult result = service.checkBlocked("client123", null, null);
        assertTrue(result.isBlocked());
        assertEquals(regexClient, result.getBlockedClient());
    }

    @Test
    void testCheckRegexMatchByUsername() {
        RegexBlockedClient regexClient = new RegexBlockedClient(
                System.currentTimeMillis() + 60000, null, "user.*",
                RegexMatchTarget.BY_USERNAME
        );
        service.addBlockedClient(regexClient);

        BlockedClientResult result = service.checkBlocked(null, "user123", null);
        assertTrue(result.isBlocked());
        assertEquals(regexClient, result.getBlockedClient());
    }

    @Test
    void testCheckRegexMatchByIpAddress() {
        RegexBlockedClient regexClient = new RegexBlockedClient(
                System.currentTimeMillis() + 60000, null, "ip.*",
                RegexMatchTarget.BY_IP_ADDRESS
        );
        service.addBlockedClient(regexClient);

        BlockedClientResult result = service.checkBlocked(null, null, "ip123");
        assertTrue(result.isBlocked());
        assertEquals(regexClient, result.getBlockedClient());
    }

    @Test
    void testBlockedClientDoNotExpire() {
        BlockedClient client = new UsernameBlockedClient(USERNAME);
        service.addBlockedClient(client);

        BlockedClientResult result = service.checkBlocked(null, USERNAME, null);
        assertTrue(result.isBlocked());
        assertNotNull(result.getBlockedClient());
    }

    @Test
    void testRegexDoesNotMatch() {
        RegexBlockedClient regexClient = new RegexBlockedClient(System.currentTimeMillis() + 60000, null,
                "user.*",
                RegexMatchTarget.BY_CLIENT_ID
        );
        service.addBlockedClient(regexClient);

        BlockedClientResult result = service.checkBlocked("client", null, null);
        assertFalse(result.isBlocked());
    }

    @Test
    void testExpiredClientIsIgnored() {
        BlockedClient expiredClient = new ClientIdBlockedClient(System.currentTimeMillis() - 60000, null, CLIENT_ID);
        service.addBlockedClient(expiredClient);

        BlockedClientResult result = service.checkBlocked(CLIENT_ID, null, null);
        assertFalse(result.isBlocked());
    }

    @Test
    void testRemoveBlockedClient() {
        BlockedClient client = new ClientIdBlockedClient(System.currentTimeMillis() + 60000, null, CLIENT_ID);
        service.addBlockedClient(client);
        service.removeBlockedClient(client);

        BlockedClientResult result = service.checkBlocked(CLIENT_ID, null, null);
        assertFalse(result.isBlocked());
    }

    @Test
    void testValidationMissingValueThrowsException() {
        BlockedClient invalidClient = new ClientIdBlockedClient();
        assertThrows(DataValidationException.class, () -> service.addBlockedClient(invalidClient));
    }

    @Test
    void testValidationMissingRegexMatchTargetThrowsException() {
        BlockedClient invalidClient = new RegexBlockedClient("abc.*", null);
        assertThrows(DataValidationException.class, () -> service.addBlockedClient(invalidClient));
    }

    @Test
    void testProcessBlockedClientUpdate_AddAndRemove() {
        BlockedClient client = new ClientIdBlockedClient(System.currentTimeMillis() + 60000, null, CLIENT_ID);
        String key = client.getKey();

        service.processBlockedClientUpdate(key, "other-service", client);
        assertNotNull(service.getBlockedClient(BlockedClientType.CLIENT_ID, key));

        service.processBlockedClientUpdate(key, "other-service", null);
        assertNull(service.getBlockedClient(BlockedClientType.CLIENT_ID, key));
    }

    @Test
    void testCleanupRemovesExpiredClients() {
        BlockedClient expired = new ClientIdBlockedClient(System.currentTimeMillis() - 70000, null, CLIENT_ID);
        service.addBlockedClient(expired);
        service.setBlockedClientCleanupTtl(1);

        // manually invoke cleanup
        service.cleanUp();

        assertNull(service.getBlockedClient(BlockedClientType.CLIENT_ID, expired.getKey()));
    }

    @Test
    void testAddBlockedClientAndPersistDelegatesCorrectly() {
        BlockedClient client = new ClientIdBlockedClient(System.currentTimeMillis() + 60000, null, CLIENT_ID);
        BlockedClientDto dto = service.addBlockedClientAndPersist(client);

        assertNotNull(dto);
        verify(producerService).persistBlockedClient(eq(client.getKey()), any(), any());
    }

    @Test
    void testRemoveBlockedClientAndPersistDelegatesCorrectly() {
        BlockedClient client = new ClientIdBlockedClient(System.currentTimeMillis() + 60000, null, CLIENT_ID);
        service.addBlockedClient(client);
        service.removeBlockedClientAndPersist(client);

        verify(producerService).persistBlockedClient(eq(client.getKey()), eq(QueueConstants.EMPTY_BLOCKED_CLIENT_PROTO), any());
    }

    @Test
    void testInitLoadsClientsCorrectly() {
        BlockedClient client = new ClientIdBlockedClient(System.currentTimeMillis() + 60000, null, CLIENT_ID);
        Map<String, BlockedClient> map = Map.of(client.getKey(), client);
        service.init(map);

        assertEquals(client, service.getBlockedClient(BlockedClientType.CLIENT_ID, client.getKey()));
    }
}
